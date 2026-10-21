package uz.salvadore.hamkorbank.archi.advisor.application.service;

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
import java.util.function.Consumer;
import java.util.function.Supplier;
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
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorAnswer;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.Finding;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ModelPlan;
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

    AdvisorService service() {
        return service(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    AdvisorService service(Clock clock) {
        return new AdvisorService(reader(), metamodel(), sessionRepository(), budgetRepository(), settings(),
                llm(), prompts(), unitOfWork(), events(), metrics(), policy, new RateLimiter(clock),
                new ActiveSessions(), clock);
    }

    AdvisorAdminService admin() {
        return new AdvisorAdminService(requested -> WORKSPACE, settings(), budgetRepository(), null, unitOfWork(),
                events(), metrics(), policy, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    ModelContextReader reader() {
        return (requester, modelId) -> new ModelContextReader.ReadModel(WORKSPACE, deleted, graph);
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
            public void save(AdvisorSession session) {
                sessions.put(session.requestedBy() + "/" + session.idempotencyKey(), session);
            }
        };
    }

    TokenBudgetRepository budgetRepository() {
        return new TokenBudgetRepository() {
            @Override
            public Optional<TokenBudget> find(WorkspaceId workspaceId, BudgetPeriod period, BudgetPolicy policy) {
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

    static UnitOfWork unitOfWork() {
        return new UnitOfWork() {
            @Override
            public <T> T write(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T read(Supplier<T> work) {
                return work.get();
            }
        };
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
        final List<Finding> findings = new ArrayList<>();
        ModelPlan plan;
        AdvisorAnswer answer;
        SessionMeta meta;

        @Override
        public void token(String delta) {
            text.append(delta);
        }

        @Override
        public void finding(Finding finding) {
            findings.add(finding);
        }

        @Override
        public void plan(ModelPlan plan) {
            this.plan = plan;
        }

        @Override
        public void answer(AdvisorAnswer answer) {
            this.answer = answer;
        }

        @Override
        public void meta(SessionMeta meta) {
            this.meta = meta;
        }
    }
}
