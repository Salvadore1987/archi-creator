package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.salvadore.hamkorbank.archi.advisor.adapter.persistence.JpaAuditLog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorEvents;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorMetrics;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSessionRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSettingsRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AuditLog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmPort;
import uz.salvadore.hamkorbank.archi.advisor.application.port.MetamodelCatalog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.ModelContextReader;
import uz.salvadore.hamkorbank.archi.advisor.application.port.PromptCatalog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.advisor.application.port.WorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.advisor.application.service.ActiveSessions;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorAdminService;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorService;
import uz.salvadore.hamkorbank.archi.advisor.application.service.RateLimiter;
import uz.salvadore.hamkorbank.archi.bootstrap.wiring.AfterCommitEventPublisher;
import uz.salvadore.hamkorbank.archi.bootstrap.wiring.TransactionalUnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.WorkspaceService;

/**
 * Сценарии помощника — бинами. Мост к modeling — только чтение: сценарий запросов
 * и пространства, ни одного сервиса записи.
 */
@Configuration
@EnableConfigurationProperties(AiSettings.class)
public class AdvisorConfiguration {

    @Bean
    AdvisorPolicy advisorPolicy(AiSettings settings) {
        return new AdvisorPolicy(settings.maxContextTokens(), settings.minCoverage(),
                settings.defaultMonthlyTokenLimit(), settings.maxOutputTokens(), settings.userRequestsPerMinute(),
                settings.workspaceRequestsPerMinute(), settings.budgetZone());
    }

    @Bean
    JpaAuditLog.BudgetZone budgetZone(AiSettings settings) {
        return new JpaAuditLog.BudgetZone(settings.budgetZone());
    }

    @Bean
    ModelContextReader modelContextReader(ModelQueryService models) {
        return new ModelingContextReader(models);
    }

    @Bean
    MetamodelCatalog metamodelCatalog() {
        return new ArchimateMetamodelCatalog();
    }

    @Bean
    WorkspaceDirectory workspaceDirectory(WorkspaceService workspaces) {
        return new ModelingWorkspaceDirectory(workspaces);
    }

    @Bean
    LlmPort llmPort(AiSettings settings) {
        return new AnthropicLlmClient(settings);
    }

    @Bean
    PromptCatalog promptCatalog() {
        return new ClasspathPromptCatalog();
    }

    @Bean
    UnitOfWork advisorUnitOfWork(TransactionalUnitOfWork transactions) {
        return new UnitOfWork() {
            @Override
            public <T> T write(Supplier<T> work) {
                return transactions.write(work);
            }

            @Override
            public <T> T read(Supplier<T> work) {
                return transactions.read(work);
            }
        };
    }

    @Bean
    AdvisorEvents advisorEvents(AfterCommitEventPublisher publisher) {
        return publisher::publish;
    }

    @Bean
    AdvisorMetrics advisorMetrics(MeterRegistry registry) {
        return new AdvisorMicrometerMetrics(registry);
    }

    @Bean
    BudgetNotifications budgetNotifications() {
        return new BudgetNotifications();
    }

    @Bean
    AdvisorService advisorService(ModelContextReader reader, MetamodelCatalog metamodel,
                                  AdvisorSessionRepository sessions, TokenBudgetRepository budgets,
                                  AdvisorSettingsRepository settings, LlmPort llm, PromptCatalog prompts,
                                  UnitOfWork advisorUnitOfWork, AdvisorEvents events, AdvisorMetrics metrics,
                                  AdvisorPolicy policy, Clock clock) {
        return new AdvisorService(reader, metamodel, sessions, budgets, settings, llm, prompts, advisorUnitOfWork,
                events, metrics, policy, new RateLimiter(clock), new ActiveSessions(), clock);
    }

    @Bean
    AdvisorAdminService advisorAdminService(WorkspaceDirectory workspaces, AdvisorSettingsRepository settings,
                                            TokenBudgetRepository budgets, AuditLog audit,
                                            UnitOfWork advisorUnitOfWork, AdvisorEvents events,
                                            AdvisorMetrics metrics, AdvisorPolicy policy, Clock clock) {
        return new AdvisorAdminService(workspaces, settings, budgets, audit, advisorUnitOfWork, events, metrics,
                policy, clock);
    }
}
