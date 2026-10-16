package uz.salvadore.hamkorbank.archi.advisor.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AccessPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AdvisorOperation;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorEvents;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorMetrics;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSessionRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSettingsRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmListener;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmPort;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmRequest;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmStream;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmUnavailableException;
import uz.salvadore.hamkorbank.archi.advisor.application.port.MetamodelCatalog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.ModelContextReader;
import uz.salvadore.hamkorbank.archi.advisor.application.port.PromptCatalog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository.BudgetPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ModelContext;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AuditEntryId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.TokenBudgetId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
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
 *   <li>Роль. Повтор ключа — результат существующей сессии без нового обращения.</li>
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
public final class AdvisorService {

    private static final System.Logger LOG = System.getLogger(AdvisorService.class.getName());

    private final ModelContextReader reader;
    private final MetamodelCatalog metamodel;
    private final AdvisorSessionRepository sessions;
    private final TokenBudgetRepository budgets;
    private final AdvisorSettingsRepository settings;
    private final LlmPort llm;
    private final PromptCatalog prompts;
    private final UnitOfWork unitOfWork;
    private final AdvisorEvents events;
    private final AdvisorMetrics metrics;
    private final AdvisorPolicy policy;
    private final RateLimiter rateLimiter;
    private final ActiveSessions active;
    private final Clock clock;
    private final UuidV7 uuids;
    private final ContextPlanner planner;
    private final ResultReader results;

    public AdvisorService(ModelContextReader reader, MetamodelCatalog metamodel, AdvisorSessionRepository sessions,
                          TokenBudgetRepository budgets, AdvisorSettingsRepository settings, LlmPort llm,
                          PromptCatalog prompts, UnitOfWork unitOfWork, AdvisorEvents events, AdvisorMetrics metrics,
                          AdvisorPolicy policy, RateLimiter rateLimiter, ActiveSessions active, Clock clock) {
        this.reader = reader;
        this.metamodel = metamodel;
        this.sessions = sessions;
        this.budgets = budgets;
        this.settings = settings;
        this.llm = llm;
        this.prompts = prompts;
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.metrics = metrics;
        this.policy = policy;
        this.rateLimiter = rateLimiter;
        this.active = active;
        this.clock = clock;
        this.uuids = new UuidV7(clock);
        this.planner = new ContextPlanner(policy);
        this.results = new ResultReader(metamodel, () -> FindingId.next(uuids));
    }

    /**
     * Обратиться к помощнику. Все отказы — исключением отсюда, до потока; полученный
     * поток отказом уже не начнётся.
     *
     * @param language язык ответа — язык интерфейса автора
     */
    public AdvisorStream ask(Requester requester, AdvisorRequest request, String idempotencyKey, Locale language) {
        AdvisorOperation operation = AdvisorOperation.of(request.function());
        return metrics.observe(operation.useCase(), () -> {
            AccessPolicy.require(requester, operation);
            Optional<AdvisorSession> existing = unitOfWork.read(
                    () -> sessions.findByKey(requester.subject(), idempotencyKey));
            if (existing.isPresent()) {
                return replay(existing.get(), request);
            }
            ModelContextReader.ReadModel model = reader.read(requester, request.modelId());
            Instant now = clock.instant();
            AdvisorSession session = AdvisorSession.request(AdvisorSessionId.next(uuids), model.workspaceId(), request,
                    idempotencyKey, requester.subject(), now);
            if (model.deleted()) {
                throw reject(session, RejectionReason.MODEL_DELETED, Map.of());
            }
            BudgetPeriod period = BudgetPeriod.containing(now, policy.budgetZone());
            BudgetPolicy budgetPolicy = budgetPolicy(model.workspaceId());
            TokenBudget budget = unitOfWork.read(() -> budgets.find(model.workspaceId(), period, budgetPolicy))
                    .orElseGet(() -> TokenBudget.open(TokenBudgetId.next(uuids), model.workspaceId(), period,
                            budgetPolicy.limitTokens(), budgetPolicy.enabled()));
            Optional<RejectionReason> refused = budget.admission();
            if (refused.isPresent()) {
                throw reject(session, refused.get(), refused.get() == RejectionReason.BUDGET_EXHAUSTED
                        ? Map.of("resetsAt", period.resetsAt().toString()) : Map.of());
            }
            Optional<Duration> wait = rateLimiter.acquire(Map.of(
                    "user:" + requester.subject(), policy.userRequestsPerMinute(),
                    "workspace:" + model.workspaceId(), policy.workspaceRequestsPerMinute()));
            if (wait.isPresent()) {
                long seconds = Math.max(1, (wait.get().toMillis() + 999) / 1000);
                throw reject(session, RejectionReason.RATE_LIMITED, Map.of("retryAfterSeconds", seconds));
            }
            ModelContext context = planner.plan(request, model.graph())
                    .orElseThrow(() -> reject(session, RejectionReason.CONTEXT_TOO_LARGE, Map.of()));
            if (context.truncated()) {
                metrics.contextTruncated(request.function());
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
            LOG.log(System.Logger.Level.WARNING, "advisor upstream unavailable: {0}",
                    unavailable.getCause() == null ? "-" : unavailable.getCause().getClass().getName());
            finish(session, () -> session.fail(TokenUsage.NONE, AuditEntryId.next(uuids), clock.instant()));
            throw new AdvisorDomainException(AdvisorCodes.UNAVAILABLE, Failure.UNAVAILABLE,
                    Message.of(AdvisorMessages.UNAVAILABLE));
        }
        try {
            session.startStreaming(context.tokenEstimate(), context.truncated());
            unitOfWork.write(() -> {
                sessions.save(session);
                return null;
            });
        } catch (RuntimeException e) {
            upstream.cancel();
            throw e;
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
            case REJECTED -> throw rejection(Objects.requireNonNull(session.rejectionReason()), Map.of());
            case COMPLETED, FAILED -> new AdvisorStream() {
                @Override
                public AdvisorSessionId sessionId() {
                    return session.id();
                }

                @Override
                public void run(StreamSink sink) {
                    session.findings().forEach(sink::finding);
                    if (session.plan() != null) {
                        sink.plan(session.plan());
                    }
                    if (session.answer() != null) {
                        sink.answer(session.answer());
                    }
                    sink.meta(SessionMeta.of(session));
                }
            };
        };
    }

    private BudgetPolicy budgetPolicy(WorkspaceId workspaceId) {
        AdvisorSettingsRepository.StoredSettings stored = unitOfWork.read(() -> settings.find(workspaceId));
        return new BudgetPolicy(stored.enabled(), Objects.requireNonNullElse(stored.monthlyTokenLimit(),
                policy.defaultMonthlyTokenLimit()));
    }

    /** Записать отказ сессией с аудитом и вернуть исключение для ответа. */
    private AdvisorDomainException reject(AdvisorSession session, RejectionReason reason, Map<String, Object> details) {
        finish(session, () -> session.reject(reason, AuditEntryId.next(uuids), clock.instant()));
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
     * события — одной транзакцией. Отказ ничего не расходует, бюджет не трогается.
     */
    private void finish(AdvisorSession session, Runnable transition) {
        transition.run();
        unitOfWork.write(() -> {
            sessions.save(session);
            List<DomainEvent> published = new ArrayList<>(session.pullEvents());
            if (session.usage().total() > 0) {
                BudgetPeriod period = BudgetPeriod.containing(session.requestedAt(), policy.budgetZone());
                BudgetPolicy budgetPolicy = budgetPolicy(session.workspaceId());
                TokenBudget budget = budgets.lock(session.workspaceId(), period, budgetPolicy,
                        TokenBudgetId.next(uuids));
                budget.record(session.usage(), clock.instant());
                budgets.save(budget);
                published.addAll(budget.pullEvents());
                metrics.budget(budget);
            }
            events.publish(published);
            return null;
        });
        metrics.session(session.function(), session.status(), session.rejectionReason());
        if (session.usage().total() > 0) {
            metrics.tokens(session.function(), session.workspaceId().toString(), session.usage());
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
        public AdvisorSessionId sessionId() {
            return session.id();
        }

        @Override
        public void run(StreamSink sink) {
            StringBuilder text = new StringBuilder();
            AtomicReference<ModelPlan> plan = new AtomicReference<>();
            AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.NONE);
            try {
                upstream.consume(new LlmListener() {
                    @Override
                    public void text(String delta) {
                        if (session.firstTokenAt() == null) {
                            Instant now = clock.instant();
                            session.firstToken(now);
                            metrics.firstToken(session.function(), Duration.between(session.requestedAt(), now));
                        }
                        text.append(delta);
                        sink.token(delta);
                    }

                    @Override
                    public void toolCall(String name, Map<String, Object> input) {
                        if (session.function() == AdvisorFunction.REVIEW && AdvisorTools.REPORT_FINDING.equals(name)) {
                            results.finding(input, context).ifPresent(finding -> {
                                session.addFinding(finding);
                                sink.finding(finding);
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
                    finish(session, () -> session.fail(usage.get(), AuditEntryId.next(uuids), clock.instant()));
                } else {
                    AdvisorAnswer answer = session.function() == AdvisorFunction.EXPLAIN
                            || session.function() == AdvisorFunction.DOCUMENT
                            ? results.answer(text.toString(), context) : null;
                    finish(session, () -> session.complete(usage.get(), proposed, answer, AuditEntryId.next(uuids),
                            clock.instant()));
                    if (proposed != null) {
                        sink.plan(proposed);
                    }
                    if (answer != null) {
                        sink.answer(answer);
                    }
                }
                sink.meta(SessionMeta.of(session));
            } catch (LlmUnavailableException | RuntimeException broken) {
                upstream.cancel();
                LOG.log(System.Logger.Level.WARNING, "advisor stream broken: session {0}, {1}", session.id(),
                        broken.getClass().getName());
                if (!session.status().terminal()) {
                    finish(session, () -> session.fail(usage.get(), AuditEntryId.next(uuids), clock.instant()));
                }
                try {
                    sink.meta(SessionMeta.of(session));
                } catch (RuntimeException clientGone) {
                    // Клиент ушёл — сообщить итог некому; сессия и расход уже записаны.
                    LOG.log(System.Logger.Level.INFO, "advisor stream: client gone before result, session {0}",
                            session.id());
                }
            } finally {
                active.release(session.requestedBy(), upstream);
            }
        }
    }
}
