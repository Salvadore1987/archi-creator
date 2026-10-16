package uz.salvadore.hamkorbank.archi.advisor.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AccessPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AdvisorOperation;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorEvents;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorMetrics;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSettingsRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AuditLog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository.BudgetPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.advisor.application.port.WorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetStatus;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.TokenBudgetId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Управление помощником: состояние для интерфейса, выключатель и лимит
 * для администратора, расход по пользователям и функциям.
 */
public final class AdvisorAdminService {

    private final WorkspaceDirectory workspaces;
    private final AdvisorSettingsRepository settings;
    private final TokenBudgetRepository budgets;
    private final AuditLog audit;
    private final UnitOfWork unitOfWork;
    private final AdvisorEvents events;
    private final AdvisorMetrics metrics;
    private final AdvisorPolicy policy;
    private final Clock clock;
    private final UuidV7 uuids;

    public AdvisorAdminService(WorkspaceDirectory workspaces, AdvisorSettingsRepository settings,
                               TokenBudgetRepository budgets, AuditLog audit, UnitOfWork unitOfWork,
                               AdvisorEvents events, AdvisorMetrics metrics, AdvisorPolicy policy, Clock clock) {
        this.workspaces = workspaces;
        this.settings = settings;
        this.budgets = budgets;
        this.audit = audit;
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.metrics = metrics;
        this.policy = policy;
        this.clock = clock;
        this.uuids = new UuidV7(clock);
    }

    /** Включён ли помощник и в каком состоянии бюджет месяца — по нему интерфейс показывает панель. */
    public Status status(Requester requester, Optional<WorkspaceId> requested) {
        return metrics.observe(AdvisorOperation.ADVISOR_STATUS.useCase(), () -> {
            AccessPolicy.require(requester, AdvisorOperation.ADVISOR_STATUS);
            WorkspaceId workspaceId = workspaces.resolve(requested);
            BudgetPeriod period = currentPeriod();
            return unitOfWork.read(() -> {
                TokenBudget budget = current(workspaceId, period);
                return new Status(workspaceId, budget.advisorEnabled(), budget.status(), period, period.resetsAt());
            });
        });
    }

    /** Выключатель и действующий лимит. */
    public Settings settings(Requester requester, Optional<WorkspaceId> requested) {
        return metrics.observe(AdvisorOperation.TOGGLE_ADVISOR.useCase(), () -> {
            AccessPolicy.require(requester, AdvisorOperation.TOGGLE_ADVISOR);
            WorkspaceId workspaceId = workspaces.resolve(requested);
            BudgetPolicy effective = unitOfWork.read(() -> policyOf(workspaceId));
            return new Settings(effective.enabled(), effective.limitTokens());
        });
    }

    /**
     * Новый выключатель и лимит. Бюджет месяца пересчитывает состояние от нового лимита;
     * расход не меняется — поднять лимит можно, списать расход нельзя.
     */
    public Settings change(Requester requester, Optional<WorkspaceId> requested, boolean enabled,
                           Optional<Long> monthlyTokenLimit) {
        return metrics.observe(AdvisorOperation.SET_TOKEN_BUDGET.useCase(), () -> {
            AccessPolicy.require(requester, AdvisorOperation.TOGGLE_ADVISOR);
            AccessPolicy.require(requester, AdvisorOperation.SET_TOKEN_BUDGET);
            long limit = monthlyTokenLimit.orElseThrow(() -> AdvisorDomainException.invalid(
                    Message.of(AdvisorMessages.FIELD_REQUIRED, "monthlyTokenLimit")));
            if (limit <= 0) {
                throw AdvisorDomainException.invalid(Message.of(AdvisorMessages.LIMIT_NOT_POSITIVE));
            }
            WorkspaceId workspaceId = workspaces.resolve(requested);
            return unitOfWork.write(() -> {
                settings.save(workspaceId, enabled, limit);
                TokenBudget budget = budgets.lock(workspaceId, currentPeriod(), new BudgetPolicy(enabled, limit),
                        TokenBudgetId.next(uuids));
                budget.changeLimit(limit, clock.instant());
                budgets.save(budget);
                events.publish(budget.pullEvents());
                metrics.budget(budget);
                return new Settings(enabled, limit);
            });
        });
    }

    /** Расход месяца: лимит, израсходовано, разбиение по пользователям и функциям — из аудита. */
    public Usage usage(Requester requester, Optional<WorkspaceId> requested, Optional<String> period) {
        return metrics.observe(AdvisorOperation.VIEW_TOKEN_USAGE.useCase(), () -> {
            AccessPolicy.require(requester, AdvisorOperation.VIEW_TOKEN_USAGE);
            BudgetPeriod month = period.map(p -> {
                try {
                    return BudgetPeriod.parse(p);
                } catch (IllegalArgumentException invalid) {
                    throw AdvisorDomainException.invalid(invalid);
                }
            }).orElseGet(this::currentPeriod);
            WorkspaceId workspaceId = workspaces.resolve(requested);
            return unitOfWork.read(() -> {
                TokenBudget budget = current(workspaceId, month);
                return new Usage(month, budget.limitTokens(), budget.consumedTokens(), budget.status(),
                        audit.byUser(workspaceId, month), audit.byFunction(workspaceId, month));
            });
        });
    }

    private TokenBudget current(WorkspaceId workspaceId, BudgetPeriod period) {
        BudgetPolicy effective = policyOf(workspaceId);
        return budgets.find(workspaceId, period, effective).orElseGet(() -> TokenBudget.open(
                TokenBudgetId.next(uuids), workspaceId, period, effective.limitTokens(), effective.enabled()));
    }

    private BudgetPolicy policyOf(WorkspaceId workspaceId) {
        AdvisorSettingsRepository.StoredSettings stored = settings.find(workspaceId);
        return new BudgetPolicy(stored.enabled(), stored.monthlyTokenLimit().orElse(policy.defaultMonthlyTokenLimit()));
    }

    private BudgetPeriod currentPeriod() {
        return BudgetPeriod.containing(clock.instant(), policy.budgetZone());
    }

    /** Состояние помощника в пространстве. */
    public record Status(WorkspaceId workspaceId, boolean enabled, BudgetStatus budgetStatus, BudgetPeriod period,
                         LocalDate resetsAt) {
    }

    /** Выключатель и действующий месячный лимит. */
    public record Settings(boolean enabled, long monthlyTokenLimit) {
    }

    /** Расход периода с разбиением. */
    public record Usage(BudgetPeriod period, long limitTokens, long consumedTokens, BudgetStatus budgetStatus,
                        List<AuditLog.UsageRow> byUser, List<AuditLog.UsageRow> byFunction) {
    }
}
