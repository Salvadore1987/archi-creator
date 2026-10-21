package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ExplainElementCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.GenerateDocumentationCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.PlanModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ReviewModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorAnswerResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.FindingResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.ModelPlanResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.SessionMetaResponse;
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
import uz.salvadore.hamkorbank.archi.advisor.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.advisor.application.port.WorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorAdminApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorStream;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.StreamSink;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorAdminApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Role;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextElement;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextLayer;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextRelation;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ModelGraph;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.TokenBudgetId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.SessionStatus;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

/** Сценарий помощника на подменных портах: хранение в памяти, внешний API — сценарием теста. */
final class Fixture {

    static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    static final WorkspaceId WORKSPACE = WorkspaceId.of(UUIDUtil.uuid("00000000-0000-7000-8000-0000000000aa"));
    static final ModelId MODEL = ModelId.of(UUIDUtil.uuid("00000000-0000-7000-8000-0000000000bb"));
    static final Requester ALICE = Requester.of("alice", Role.ARCHITECT);
    static final Requester VIEWER = Requester.of("victor", Role.VIEWER);
    static final String APP = "archimate:ApplicationComponent";
    static final String SERVICE = "archimate:ApplicationService";
    static final String PROCESS = "archimate:BusinessProcess";
    static final String SERVING = "archimate:ServingRelationship";
    static final String REALIZATION = "archimate:RealizationRelationship";

    final Map<String, AdvisorSession> sessions = new HashMap<>();
    final Map<BudgetPeriod, TokenBudget> budgets = new HashMap<>();
    final List<DomainEvent> events = new ArrayList<>();
    final List<LlmRequest> upstreamCalls = new ArrayList<>();
    boolean enabled = true;
    Long limit = 1_000_000L;
    boolean upstreamDown;
    boolean deleted;
    Consumer<LlmListener> script = listener -> {
        listener.usage(new TokenUsage(100, 0));
        listener.text("ok");
        listener.usage(new TokenUsage(100, 10));
    };
    ModelGraph graph = graph();
    AdvisorPolicy policy = new AdvisorPolicy(120_000, 0.25, 5_000_000, 4096, 5, 20, ZoneId.of("UTC"));
    final List<FakeStream> opened = new ArrayList<>();
    /** Номер открытой транзакции; 0 — вне транзакции. */
    int transaction;
    int transactions;
    /** В какой транзакции читались настройки и бюджет. */
    int settingsReadIn;
    int budgetReadIn;
    /** Что случается, пока сценарий читает модель, — например, параллельное обращение. */
    Runnable duringRead = () -> {
    };

    static ModelGraph graph() {
        return new ModelGraph(
                List.of(new ContextElement("crm", APP, "CRM", "Клиентская система",
                                ContextLayer.APPLICATION),
                        new ContextElement("svc", SERVICE, "Карточка клиента", null,
                                ContextLayer.APPLICATION),
                        new ContextElement("onboarding", PROCESS, "Онбординг", null,
                                ContextLayer.BUSINESS)),
                List.of(new ContextRelation("r1", REALIZATION, "crm", "svc"),
                        new ContextRelation("r2", SERVING, "svc", "onboarding")),
                List.of());
    }

    Advisor service() {
        return service(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    Advisor service(Clock clock) {
        AdvisorKernel kernel = kernel(clock);
        return new Advisor(new AdvisorApplicationServiceImpl(new AdvisorSessionStartCommandHandler(kernel,
                budgets(kernel), reader(), metamodel(), sessionRepository(), llm(), prompts(), policy,
                new RateLimiter(clock), new ActiveSessions())));
    }

    AdvisorAdminApplicationService admin() {
        AdvisorKernel kernel = kernel(Clock.fixed(NOW, ZoneOffset.UTC));
        AdvisorBudgets budgets = budgets(kernel);
        WorkspaceDirectory workspaces = requested -> WORKSPACE;
        return new AdvisorAdminApplicationServiceImpl(new AdvisorStatusGetQueryHandler(kernel, budgets, workspaces),
                new AdvisorSettingsGetQueryHandler(kernel, budgets, workspaces),
                new AdvisorSettingsConfigureCommandHandler(kernel, budgets, settings(), workspaces),
                new TokenUsageGetQueryHandler(kernel, budgets, workspaces, null));
    }

    private AdvisorKernel kernel(Clock clock) {
        return new AdvisorKernel(unitOfWork(), events(), metrics(), clock);
    }

    private AdvisorBudgets budgets(AdvisorKernel kernel) {
        return new AdvisorBudgets(kernel, settings(), budgetRepository(), policy);
    }

    /** Обращение к помощнику в тестах — запросом домена, как его строит каждый тест. */
    record Advisor(AdvisorApplicationService port) {

        AdvisorStream ask(Requester requester, AdvisorRequest request, String key, Locale language) {
            UUID model = request.modelId().value();
            UUID view = request.viewId() == null ? null : request.viewId().value();
            return switch (request.function()) {
                case REVIEW -> port.reviewModel(requester, new ReviewModelCommand(model, view, key, language));
                case EXPLAIN -> port.explainElement(requester,
                        new ExplainElementCommand(model, request.elementId(), key, language));
                case PLAN -> port.planModel(requester, new PlanModelCommand(model, request.prompt(), key, language));
                case DOCUMENT -> port.generateDocumentation(requester,
                        new GenerateDocumentationCommand(model, view, request.elementId(), key, language));
            };
        }
    }

    ModelContextReader reader() {
        return (requester, modelId) -> {
            duringRead.run();
            return new ModelContextReader.ReadModel(WORKSPACE, deleted, graph);
        };
    }

    static MetamodelCatalog metamodel() {
        return new MetamodelCatalog() {
            @Override
            public List<String> elementTypes() {
                return List.of(APP, SERVICE, PROCESS);
            }

            @Override
            public List<String> relationshipTypes() {
                return List.of(SERVING, REALIZATION);
            }

            @Override
            public Map<String, List<String>> permittedFrom(String sourceType) {
                return APP.equals(sourceType) ? Map.of(REALIZATION, List.of(SERVICE), SERVING, List.of(PROCESS))
                        : Map.of();
            }

            @Override
            public boolean permitted(String sourceType, String targetType, String relationshipType) {
                return permittedFrom(sourceType).getOrDefault(relationshipType, List.of()).contains(targetType);
            }
        };
    }

    AdvisorSessionRepository sessionRepository() {
        return new AdvisorSessionRepository() {
            @Override
            public Optional<AdvisorSession> findByKey(String requestedBy, String idempotencyKey) {
                return Optional.ofNullable(sessions.get(requestedBy + "/" + idempotencyKey));
            }

            @Override
            public boolean claim(AdvisorSession session) {
                return sessions.putIfAbsent(session.requestedBy() + "/" + session.idempotencyKey(), session) == null;
            }

            @Override
            public void save(AdvisorSession session) {
                sessions.put(session.requestedBy() + "/" + session.idempotencyKey(), session);
            }
        };
    }

    TokenBudgetRepository budgetRepository() {
        return new TokenBudgetRepository() {
            @Override
            public Optional<TokenBudget> find(WorkspaceId workspaceId, BudgetPeriod period, BudgetPolicy policy) {
                budgetReadIn = transaction;
                return Optional.ofNullable(budgets.get(period)).map(b -> copy(b, policy));
            }

            @Override
            public TokenBudget lock(WorkspaceId workspaceId, BudgetPeriod period, BudgetPolicy policy,
                                    TokenBudgetId newId) {
                return find(workspaceId, period, policy).orElseGet(
                        () -> TokenBudget.open(newId, workspaceId, period, policy.limitTokens(), policy.enabled()));
            }

            @Override
            public void save(TokenBudget budget) {
                budgets.put(budget.period(), budget);
            }

            private TokenBudget copy(TokenBudget b, BudgetPolicy policy) {
                return TokenBudget.restore(b.id(), b.workspaceId(), b.period(), policy.limitTokens(),
                        b.consumedTokens(), b.warningThresholdPercent(), policy.enabled(), b.version());
            }
        };
    }

    AdvisorSettingsRepository settings() {
        return new AdvisorSettingsRepository() {
            @Override
            public StoredSettings find(WorkspaceId workspaceId) {
                settingsReadIn = transaction;
                return new StoredSettings(enabled, limit);
            }

            @Override
            public void save(WorkspaceId workspaceId, boolean on, long monthlyTokenLimit) {
                enabled = on;
                limit = monthlyTokenLimit;
            }
        };
    }

    LlmPort llm() {
        return request -> {
            if (upstreamDown) {
                throw new LlmUnavailableException(new IOException());
            }
            upstreamCalls.add(request);
            FakeStream stream = new FakeStream(script);
            opened.add(stream);
            return stream;
        };
    }

    static PromptCatalog prompts() {
        return new PromptCatalog() {
            @Override
            public String system(AdvisorFunction function, Locale answerLanguage) {
                return "system " + function + " " + answerLanguage.getLanguage();
            }

            @Override
            public String toolDescription(String toolName) {
                return toolName;
            }
        };
    }

    /** Транзакция в памяти: номер текущей, вложенная — ошибка сценария, а не молчаливое присоединение. */
    UnitOfWork unitOfWork() {
        return new UnitOfWork() {
            @Override
            public <T> T write(Supplier<T> work) {
                return inTransaction(work);
            }

            @Override
            public <T> T read(Supplier<T> work) {
                return inTransaction(work);
            }
        };
    }

    private <T> T inTransaction(Supplier<T> work) {
        if (transaction != 0) {
            throw new AssertionError("вложенная транзакция");
        }
        transaction = ++transactions;
        try {
            return work.get();
        } finally {
            transaction = 0;
        }
    }

    AdvisorEvents events() {
        return published -> events.addAll(published);
    }

    static AdvisorMetrics metrics() {
        return new AdvisorMetrics() {
            @Override
            public <T> T observe(String useCase, Supplier<T> work) {
                return work.get();
            }

            @Override
            public void session(AdvisorFunction function, SessionStatus outcome, RejectionReason reason) {
            }

            @Override
            public void firstToken(AdvisorFunction function, Duration sinceRequest) {
            }

            @Override
            public void tokens(AdvisorFunction function, String workspace, TokenUsage usage) {
            }

            @Override
            public void budget(TokenBudget budget) {
            }

            @Override
            public void contextTruncated(AdvisorFunction function) {
            }
        };
    }

    AdvisorSession onlySession() {
        return sessions.values().stream().reduce((a, b) -> {
            throw new AssertionError("сессий больше одной");
        }).orElseThrow();
    }

    /** Поток, который проигрывает сценарий теста. */
    static final class FakeStream implements LlmStream {

        private final Consumer<LlmListener> script;
        volatile boolean cancelled;

        FakeStream(Consumer<LlmListener> script) {
            this.script = script;
        }

        @Override
        public void consume(LlmListener listener) {
            try {
                script.accept(listener);
            } catch (Broken broken) {
                throw new LlmUnavailableException(broken);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    /** Обрыв внешнего API посреди сценария. */
    static final class Broken extends RuntimeException {
    }

    /** Кадры потока, собранные для проверки. */
    static class Recorded implements StreamSink {

        final StringBuilder text = new StringBuilder();
        final List<FindingResponse> findings = new ArrayList<>();
        ModelPlanResponse plan;
        AdvisorAnswerResponse answer;
        SessionMetaResponse meta;

        @Override
        public void token(String delta) {
            text.append(delta);
        }

        @Override
        public void finding(FindingResponse finding) {
            findings.add(finding);
        }

        @Override
        public void plan(ModelPlanResponse plan) {
            this.plan = plan;
        }

        @Override
        public void answer(AdvisorAnswerResponse answer) {
            this.answer = answer;
        }

        @Override
        public void meta(SessionMetaResponse meta) {
            this.meta = meta;
        }
    }
}
