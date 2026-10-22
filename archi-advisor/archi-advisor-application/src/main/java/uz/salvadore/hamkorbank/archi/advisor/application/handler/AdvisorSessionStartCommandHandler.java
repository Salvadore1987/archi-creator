package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AdvisorOperation;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ExplainElementCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.GenerateDocumentationCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.PlanModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ReviewModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.mapper.AdvisorDataMapper;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSessionRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmListener;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmPort;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmRequest;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmStream;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmUnavailableException;
import uz.salvadore.hamkorbank.archi.advisor.application.port.MetamodelCatalog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.ModelContextReader;
import uz.salvadore.hamkorbank.archi.advisor.application.port.PromptCatalog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorStream;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.StreamSink;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ModelContext;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AuditEntryId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorAnswer;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ModelPlan;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;
/**
 * Обращение к помощнику — четыре функции одним сценарием: ревью, подсказка, план,
 * документация. Различаются они контекстом, инструментами и формой результата,
 * а порядок у всех один.
 *
 * <ol>
 *   <li>Роль. Повтор ключа — результат существующей сессии без нового обращения. Новая сессия
 *       занимает ключ записью до любого отказа и до внешнего API: параллельный повтор того же
 *       ключа не открывает второй поток.</li>
 *   <li>Отказы — до внешнего API: выключатель, бюджет, частота, размер контекста.
 *       Отказ записывается сессией {@code REJECTED} с аудитом.</li>
 *   <li>Открытие потока у внешнего API синхронно: его недоступность — честный
 *       отказ до первого кадра.</li>
 *   <li>Поток: текст, находки, план. По концу — расход списывается с бюджета в той
 *       же транзакции, что фиксирует сессию, даже если поток оборвался.</li>
 * </ol>
 *
 * <p>Ни один шаг не пишет в модель: порта записи в modeling у помощника нет.
 */
public final class AdvisorSessionStartCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorSessionStartCommandHandler.class);

    private final AdvisorKernel kernel;
    private final AdvisorBudgets budgets;
    private final ModelContextReader reader;
    private final MetamodelCatalog metamodel;
    private final AdvisorSessionRepository sessions;
    private final LlmPort llm;
    private final PromptCatalog prompts;
    private final AdvisorPolicy policy;
    private final RateLimiter rateLimiter;
    private final ActiveSessions active;
    private final ContextPlanner planner;
    private final ResultReader results;

    public AdvisorSessionStartCommandHandler(AdvisorKernel kernel, AdvisorBudgets budgets, ModelContextReader reader,
                                             MetamodelCatalog metamodel, AdvisorSessionRepository sessions,
                                             LlmPort llm, PromptCatalog prompts, AdvisorPolicy policy,
                                             RateLimiter rateLimiter, ActiveSessions active) {
        this.kernel = kernel;
        this.budgets = budgets;
        this.reader = reader;
        this.metamodel = metamodel;
        this.sessions = sessions;
        this.llm = llm;
        this.prompts = prompts;
        this.policy = policy;
        this.rateLimiter = rateLimiter;
        this.active = active;
        this.planner = new ContextPlanner(policy);
        this.results = new ResultReader(metamodel, () -> FindingId.next(kernel.uuids()));
    }

    /** Ревью модели или представления. */
    public AdvisorStream handle(Requester actor, ReviewModelCommand command) {
        return start(actor, AdvisorDataMapper.request(command), command.idempotencyKey(), command.language());
    }

    /** Подсказка по элементу. */
    public AdvisorStream handle(Requester actor, ExplainElementCommand command) {
        return start(actor, AdvisorDataMapper.request(command), command.idempotencyKey(), command.language());
    }

    /** План фрагмента архитектуры. */
    public AdvisorStream handle(Requester actor, PlanModelCommand command) {
        return start(actor, AdvisorDataMapper.request(command), command.idempotencyKey(), command.language());
    }

    /** Документация по модели, представлению или элементу. */
    public AdvisorStream handle(Requester actor, GenerateDocumentationCommand command) {
        return start(actor, AdvisorDataMapper.request(command), command.idempotencyKey(), command.language());
    }

    /**
     * Обратиться к помощнику. Все отказы — исключением отсюда, до потока.
     *
     * @param language язык ответа — язык интерфейса автора
     */
    private AdvisorStream start(Requester requester, AdvisorRequest request, String idempotencyKey, Locale language) {
        return kernel.run(AdvisorOperation.of(request.function()), requester, () -> {
            Optional<AdvisorSession> existing = kernel.unitOfWork().read(
                    () -> sessions.findByKey(requester.subject(), idempotencyKey));
            if (existing.isPresent()) {
                return replay(existing.get(), request);
            }
            ModelContextReader.ReadModel model = reader.read(requester, request.modelId());
            Instant now = kernel.now();
            AdvisorSession session = AdvisorSession.request(AdvisorSessionId.next(kernel.uuids()), model.workspaceId(),
                    request,
                    idempotencyKey, requester.subject(), now);
            if (!kernel.unitOfWork().write(() -> sessions.claim(session))) {
                // Ключ занят параллельным обращением, пока читалась модель: ответ — как на повтор.
                return replay(kernel.unitOfWork().read(() -> sessions.findByKey(requester.subject(), idempotencyKey))
                        .orElseThrow(), request);
            }
            if (model.deleted()) {
                throw reject(session, RejectionReason.MODEL_DELETED, Map.of());
            }
            BudgetPeriod period = budgets.period(now);
            TokenBudget budget = kernel.unitOfWork().read(() -> budgets.current(model.workspaceId(), period));
            Optional<RejectionReason> refused = budget.admission();
            if (refused.isPresent()) {
                throw reject(session, refused.get(), refused.get() == RejectionReason.BUDGET_EXHAUSTED
                        ? resetDetails(period) : Map.of());
            }
            Optional<Duration> wait = rateLimiter.acquire(Map.of(
                    "user:" + requester.subject(), policy.userRequestsPerMinute(),
                    "workspace:" + model.workspaceId(), policy.workspaceRequestsPerMinute()));
            if (wait.isPresent()) {
                throw reject(session, RejectionReason.RATE_LIMITED, waitDetails(wait.get()));
            }
            ModelContext context = planner.plan(request, model.graph())
                    .orElseThrow(() -> reject(session, RejectionReason.CONTEXT_TOO_LARGE, Map.of()));
            if (context.truncated()) {
                kernel.metrics().contextTruncated(request.function());
            }
            return open(requester, request, session, context, language);
        });
    }

    private AdvisorStream open(Requester requester, AdvisorRequest request, AdvisorSession session,
                               ModelContext context, Locale language) {
        LlmStream upstream;
        try {
            upstream = llm.open(prompt(request, context, language));
        } catch (LlmUnavailableException unavailable) {
            LOG.error("advisor upstream unavailable: {}",
                    unavailable.getCause() == null ? "-" : unavailable.getCause().getClass().getName());
            finish(session, () -> session.fail(TokenUsage.NONE, AuditEntryId.next(kernel.uuids()), kernel.now()));
            throw new AdvisorDomainException(AdvisorCodes.UNAVAILABLE, Failure.UNAVAILABLE,
                    Message.of(AdvisorMessages.UNAVAILABLE));
        }
        boolean started = false;
        try {
            session.startStreaming(context.tokenEstimate(), context.truncated());
            kernel.unitOfWork().write(() -> {
                sessions.save(session);
                return null;
            });
            started = true;
        } finally {
            if (!started) {
                upstream.cancel();
            }
        }
        active.replace(requester.subject(), upstream);
        return new LiveStream(session, context, upstream);
    }

    private LlmRequest prompt(AdvisorRequest request, ModelContext context, Locale language) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("task", request.function().name());
        header.put("scope", request.viewId() != null ? "VIEW"
                : request.elementId() != null ? "ELEMENT" : "MODEL");
        header.put("truncated", context.truncated());
        if (request.elementId() != null) {
            header.put("focus", request.elementId());
        }
        if (request.function() == AdvisorFunction.EXPLAIN) {
            String type = context.elements().stream().filter(e -> e.archiId().equals(request.elementId()))
                    .findFirst().map(e -> e.archiType()).orElse("");
            header.put("permittedRelations", metamodel.permittedFrom(type));
        }
        if (request.prompt() != null) {
            header.put("request", request.prompt());
        }
        header.put("model", new ContextJson.Raw(ContextJson.model(context)));
        return new LlmRequest(prompts.system(request.function(), language), ContextJson.object(header),
                AdvisorTools.forFunction(request.function(), metamodel, prompts), policy.maxOutputTokens());
    }

    /** Повтор ключа: тот же запрос — результат существующей сессии, другой — конфликт. */
    private AdvisorStream replay(AdvisorSession session, AdvisorRequest request) {
        if (!session.sameRequest(request)) {
            throw new AdvisorDomainException(AdvisorCodes.IDEMPOTENCY, Failure.CONFLICT,
                    Message.of(AdvisorMessages.IDEMPOTENCY_KEY_REUSED, session.idempotencyKey()));
        }
        return switch (session.status()) {
            case REQUESTED, STREAMING -> throw new AdvisorDomainException(AdvisorCodes.IDEMPOTENCY, Failure.CONFLICT,
                    Message.of(AdvisorMessages.SESSION_IN_PROGRESS, session.idempotencyKey()));
            case REJECTED -> throw rejection(Objects.requireNonNull(session.rejectionReason()),
                    replayedDetails(session));
            case COMPLETED, FAILED -> new AdvisorStream() {
                @Override
                public UUID sessionId() {
                    return session.id().value();
                }

                @Override
                public void run(StreamSink sink) {
                    session.findings().forEach(finding -> sink.finding(AdvisorDataMapper.finding(finding)));
                    if (session.plan() != null) {
                        sink.plan(AdvisorDataMapper.plan(session.plan()));
                    }
                    if (session.answer() != null) {
                        sink.answer(AdvisorDataMapper.answer(session.answer()));
                    }
                    sink.meta(AdvisorDataMapper.meta(session));
                }
            };
        };
    }

    /**
     * Детали отказа отклонённой сессии при повторе ключа: дата сброса — по месяцу обращения,
     * ожидание — остаток минуты окна частоты от обращения.
     */
    private Map<String, Object> replayedDetails(AdvisorSession session) {
        return switch (Objects.requireNonNull(session.rejectionReason())) {
            case BUDGET_EXHAUSTED -> resetDetails(budgets.period(session.requestedAt()));
            case RATE_LIMITED -> waitDetails(Duration.between(kernel.now(),
                    session.requestedAt().plus(RateLimiter.WINDOW)));
            case ADVISOR_DISABLED, CONTEXT_TOO_LARGE, MODEL_DELETED -> Map.of();
        };
    }

    private static Map<String, Object> resetDetails(BudgetPeriod period) {
        return Map.of("resetsAt", period.resetsAt().toString());
    }

    /** Через сколько секунд повторить — целое вверх, не меньше секунды. */
    private static Map<String, Object> waitDetails(Duration wait) {
        return Map.of("retryAfterSeconds", Math.max(1, (wait.toMillis() + 999) / 1000));
    }

    /** Записать отказ сессией с аудитом и вернуть исключение для ответа. */
    private AdvisorDomainException reject(AdvisorSession session, RejectionReason reason, Map<String, Object> details) {
        finish(session, () -> session.reject(reason, AuditEntryId.next(kernel.uuids()), kernel.now()));
        return rejection(reason, details);
    }

    private static AdvisorDomainException rejection(RejectionReason reason, Map<String, Object> details) {
        return switch (reason) {
            case ADVISOR_DISABLED -> new AdvisorDomainException(AdvisorCodes.ADVISOR_DISABLED, Failure.FORBIDDEN,
                    Message.of(AdvisorMessages.ADVISOR_DISABLED), details);
            case BUDGET_EXHAUSTED -> new AdvisorDomainException(AdvisorCodes.BUDGET_EXHAUSTED,
                    Failure.TOO_MANY_REQUESTS,
                    Message.of(AdvisorMessages.BUDGET_EXHAUSTED, details.getOrDefault("resetsAt", "")), details);
            case RATE_LIMITED -> new AdvisorDomainException(AdvisorCodes.RATE_LIMITED, Failure.TOO_MANY_REQUESTS,
                    Message.of(AdvisorMessages.RATE_LIMITED, details.getOrDefault("retryAfterSeconds", "")), details);
            case CONTEXT_TOO_LARGE -> new AdvisorDomainException(AdvisorCodes.CONTEXT_TOO_LARGE, Failure.TOO_LARGE,
                    Message.of(AdvisorMessages.CONTEXT_TOO_LARGE), details);
            case MODEL_DELETED -> new AdvisorDomainException(AdvisorCodes.MODEL_DELETED, Failure.UNPROCESSABLE,
                    Message.of(AdvisorMessages.MODEL_DELETED), details);
        };
    }

    /**
     * Терминальный переход и его фиксация: сессия с аудитом, расход в бюджете периода,
     * события — одной транзакцией; метрики — после коммита. Отказ ничего не расходует,
     * бюджет не трогается.
     */
    private void finish(AdvisorSession session, Runnable transition) {
        transition.run();
        Optional<TokenBudget> charged = kernel.unitOfWork().write(() -> {
            sessions.save(session);
            List<DomainEvent> published = new ArrayList<>(session.pullEvents());
            TokenBudget budget = null;
            if (session.usage().total() > 0) {
                budget = budgets.lock(session.workspaceId(), budgets.period(session.requestedAt()),
                        budgets.policyOf(session.workspaceId()));
                budget.record(session.usage(), kernel.now());
                budgets.save(budget);
                published.addAll(budget.pullEvents());
            }
            kernel.events().publish(published);
            return Optional.ofNullable(budget);
        });
        charged.ifPresent(kernel.metrics()::budget);
        kernel.metrics().session(session.function(), session.status(), session.rejectionReason());
        if (session.usage().total() > 0) {
            kernel.metrics().tokens(session.function(), session.workspaceId().toString(), session.usage());
        }
    }

    /** Поток от внешнего API: кадры клиенту по мере прихода, итог — по концу. */
    private final class LiveStream implements AdvisorStream {

        private final AdvisorSession session;
        private final ModelContext context;
        private final LlmStream upstream;

        LiveStream(AdvisorSession session, ModelContext context, LlmStream upstream) {
            this.session = session;
            this.context = context;
            this.upstream = upstream;
        }

        @Override
        public UUID sessionId() {
            return session.id().value();
        }

        /** Расход по последнему кадру внешнего API; до первого кадра — ноль. */
        private static TokenUsage spent(AtomicReference<TokenUsage> usage) {
            return Objects.requireNonNullElse(usage.get(), TokenUsage.NONE);
        }

        @Override
        public void run(StreamSink sink) {
            StringBuilder text = new StringBuilder();
            AtomicReference<ModelPlan> plan = new AtomicReference<>();
            AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.NONE);
            Map<String, String> names = context.names();
            try {
                upstream.consume(new LlmListener() {
                    @Override
                    public void text(String delta) {
                        if (session.firstTokenAt() == null) {
                            Instant now = kernel.now();
                            session.firstToken(now);
                            kernel.metrics().firstToken(session.function(),
                                    Duration.between(session.requestedAt(), now));
                        }
                        text.append(delta);
                        sink.token(delta);
                    }

                    @Override
                    public void toolCall(String name, Map<String, Object> input) {
                        if (session.function() == AdvisorFunction.REVIEW && AdvisorTools.REPORT_FINDING.equals(name)) {
                            results.finding(input, names).ifPresent(finding -> {
                                session.addFinding(finding);
                                sink.finding(AdvisorDataMapper.finding(finding));
                            });
                        } else if (session.function() == AdvisorFunction.PLAN
                                && AdvisorTools.PROPOSE_PLAN.equals(name)) {
                            plan.set(results.plan(input, context));
                        }
                    }

                    @Override
                    public void usage(TokenUsage cumulative) {
                        usage.set(cumulative);
                    }
                });
                ModelPlan proposed = plan.get();
                if (session.function() == AdvisorFunction.PLAN && proposed == null) {
                    finish(session, () -> session.fail(spent(usage), AuditEntryId.next(kernel.uuids()), kernel.now()));
                } else {
                    AdvisorAnswer answer = session.function() == AdvisorFunction.EXPLAIN
                            || session.function() == AdvisorFunction.DOCUMENT
                            ? results.answer(text.toString(), names) : null;
                    finish(session, () -> session.complete(spent(usage), proposed, answer,
                            AuditEntryId.next(kernel.uuids()), kernel.now()));
                    if (proposed != null) {
                        sink.plan(AdvisorDataMapper.plan(proposed));
                    }
                    if (answer != null) {
                        sink.answer(AdvisorDataMapper.answer(answer));
                    }
                }
                sink.meta(AdvisorDataMapper.meta(session));
            } catch (RuntimeException broken) { // NOPMD - расход пишется при любом обрыве, внешнего API и клиента
                upstream.cancel();
                LOG.error("advisor stream broken: session {}, {}", session.id(),
                        broken.getClass().getName());
                if (!session.status().terminal()) {
                    finish(session, () -> session.fail(spent(usage), AuditEntryId.next(kernel.uuids()), kernel.now()));
                }
                try {
                    sink.meta(AdvisorDataMapper.meta(session));
                } catch (RuntimeException clientGone) { // NOPMD - адаптер потока сообщает уход клиента как угодно
                    // Клиент ушёл — сообщить итог некому; сессия и расход уже записаны.
                    LOG.info("advisor stream: client gone before result, session {}",
                            session.id());
                }
            } finally {
                active.release(session.requestedBy(), upstream);
            }
        }
    }
}
