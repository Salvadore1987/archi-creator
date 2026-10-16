package uz.salvadore.hamkorbank.archi.advisor.domain.budget;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.TokenBudgetId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

/**
 * Месячный лимит токенов рабочего пространства и накопленный расход. Ключ — пара
 * (пространство, период): смена месяца даёт новый бюджет, а не сбрасывает этот.
 *
 * <p>Расход только растёт — даже у неуспешной сессии: израсходованное внешним API
 * израсходовано, а вычитание при ошибке дало бы бесплатный обход лимита. Из
 * {@code EXHAUSTED} выводит только повышение лимита администратором. Порог
 * предупреждения работу не останавливает.
 */
public final class TokenBudget extends AggregateRoot<TokenBudgetId> {

    /** Порог предупреждения по умолчанию, процент израсходованного лимита. */
    public static final int DEFAULT_WARNING_PERCENT = 80;

    private final WorkspaceId workspaceId;
    private final BudgetPeriod period;
    private long limitTokens;
    private long consumedTokens;
    private final int warningThresholdPercent;
    private BudgetStatus status;
    private boolean advisorEnabled;
    private final long version;
    private final List<DomainEvent> events = new ArrayList<>();

    private TokenBudget(TokenBudgetId id, WorkspaceId workspaceId, BudgetPeriod period, long limitTokens,
                        long consumedTokens, int warningThresholdPercent, boolean advisorEnabled, long version) {
        super(id);
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.period = Objects.requireNonNull(period, "period");
        requirePositive(limitTokens);
        if (consumedTokens < 0) {
            throw new InvalidValueException(AdvisorMessages.USAGE_NEGATIVE);
        }
        if (warningThresholdPercent < 1 || warningThresholdPercent > 99) {
            throw new InvalidValueException(AdvisorMessages.THRESHOLD_INVALID, warningThresholdPercent);
        }
        this.limitTokens = limitTokens;
        this.consumedTokens = consumedTokens;
        this.warningThresholdPercent = warningThresholdPercent;
        this.advisorEnabled = advisorEnabled;
        this.version = version;
        this.status = statusFor(consumedTokens, limitTokens);
    }

    /** Бюджет нового периода: расход с нуля. */
    public static TokenBudget open(TokenBudgetId id, WorkspaceId workspaceId, BudgetPeriod period, long limitTokens,
                                   boolean advisorEnabled) {
        return new TokenBudget(id, workspaceId, period, limitTokens, 0, DEFAULT_WARNING_PERCENT, advisorEnabled, 0);
    }

    /** Бюджет из хранения. Состояние выводится из расхода и лимита, а не читается. */
    public static TokenBudget restore(TokenBudgetId id, WorkspaceId workspaceId, BudgetPeriod period,
                                      long limitTokens, long consumedTokens, int warningThresholdPercent,
                                      boolean advisorEnabled, long version) {
        return new TokenBudget(id, workspaceId, period, limitTokens, consumedTokens, warningThresholdPercent,
                advisorEnabled, version);
    }

    /**
     * Можно ли начинать сессию: выключенный помощник и исчерпанный бюджет отказывают
     * до вызова внешнего API. {@code WARNING} не отказывает.
     */
    public Optional<RejectionReason> admission() {
        if (!advisorEnabled) {
            return Optional.of(RejectionReason.ADVISOR_DISABLED);
        }
        if (status == BudgetStatus.EXHAUSTED) {
            return Optional.of(RejectionReason.BUDGET_EXHAUSTED);
        }
        return Optional.empty();
    }

    /** Учесть расход сессии — любой, включая неуспешную. */
    public void record(TokenUsage usage, Instant now) {
        consumedTokens = Math.addExact(consumedTokens, Objects.requireNonNull(usage, "usage").total());
        transition(now);
    }

    /** Новый лимит администратора. Расход не меняется: поднять лимит можно, списать расход — нет. */
    public void changeLimit(long newLimit, Instant now) {
        requirePositive(newLimit);
        limitTokens = newLimit;
        transition(now);
    }

    /** Включить или выключить помощника; расход и состояние бюджета не меняются. */
    public void toggle(boolean enabled) {
        advisorEnabled = enabled;
    }

    private void transition(Instant now) {
        BudgetStatus next = statusFor(consumedTokens, limitTokens);
        if (next.ordinal() > status.ordinal()) {
            if (next == BudgetStatus.EXHAUSTED) {
                events.add(new TokenBudgetExhaustedEvent(workspaceId, period, limitTokens, consumedTokens, now));
            } else {
                events.add(new TokenBudgetThresholdReachedEvent(workspaceId, period, limitTokens, consumedTokens,
                        warningThresholdPercent, now));
            }
        }
        status = next;
    }

    private BudgetStatus statusFor(long consumed, long limit) {
        if (consumed >= limit) {
            return BudgetStatus.EXHAUSTED;
        }
        // Сравнение в целых: consumed / limit >= percent / 100 без дробей и переполнения на разумных лимитах.
        return Math.multiplyExact(consumed, 100L) >= Math.multiplyExact(limit, (long) warningThresholdPercent)
                ? BudgetStatus.WARNING : BudgetStatus.OK;
    }

    private static void requirePositive(long limit) {
        if (limit <= 0) {
            throw new InvalidValueException(AdvisorMessages.LIMIT_NOT_POSITIVE);
        }
    }

    /** События, накопленные с последнего вызова; список очищается. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    /** Доля израсходованного — для метрики заполнения бюджета. */
    public double utilization() {
        return (double) consumedTokens / limitTokens;
    }


    /** Пространство, чей это бюджет. */
    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    /** Месяц, за который учитывается расход. */
    public BudgetPeriod period() {
        return period;
    }

    /** Действующий месячный лимит токенов. */
    public long limitTokens() {
        return limitTokens;
    }

    /** Израсходовано за период — с отказами и неуспешными сессиями. */
    public long consumedTokens() {
        return consumedTokens;
    }

    /** Процент лимита, с которого бюджет переходит в {@code WARNING}. */
    public int warningThresholdPercent() {
        return warningThresholdPercent;
    }

    /** Состояние, выведенное из расхода и лимита. */
    public BudgetStatus status() {
        return status;
    }

    /** Включён ли помощник в пространстве. */
    public boolean advisorEnabled() {
        return advisorEnabled;
    }

    /** Версия строки в хранении — для оптимистичной блокировки. */
    public long version() {
        return version;
    }
}
