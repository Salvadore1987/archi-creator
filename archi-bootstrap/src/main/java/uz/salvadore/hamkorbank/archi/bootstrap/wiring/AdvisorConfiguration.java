package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.salvadore.hamkorbank.archi.advisor.adapter.persistence.JpaAuditLog;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.ActiveSessions;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorBudgets;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorKernel;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorSessionStartCommandHandler;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorSettingsConfigureCommandHandler;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorSettingsGetQueryHandler;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorStatusGetQueryHandler;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.RateLimiter;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.TokenUsageGetQueryHandler;
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
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorAdminApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorAdminApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.bootstrap.advisor.AdvisorMicrometerMetrics;
import uz.salvadore.hamkorbank.archi.bootstrap.advisor.AiSettings;
import uz.salvadore.hamkorbank.archi.bootstrap.advisor.AnthropicLlmClient;
import uz.salvadore.hamkorbank.archi.bootstrap.advisor.ArchimateMetamodelCatalog;
import uz.salvadore.hamkorbank.archi.bootstrap.advisor.BudgetNotifications;
import uz.salvadore.hamkorbank.archi.bootstrap.advisor.ClasspathPromptCatalog;
import uz.salvadore.hamkorbank.archi.bootstrap.advisor.ModelingContextReader;
import uz.salvadore.hamkorbank.archi.bootstrap.advisor.ModelingWorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;

/**
 * Сценарии помощника — бинами. Мост к modeling — только чтение: сценарий запросов
 * и пространства, ни одного сервиса записи.
 */
@Configuration(proxyBeanMethods = false)
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
    ModelContextReader modelContextReader(ModelQueryApplicationService models) {
        return new ModelingContextReader(models);
    }

    @Bean
    MetamodelCatalog metamodelCatalog() {
        return new ArchimateMetamodelCatalog();
    }

    @Bean
    WorkspaceDirectory workspaceDirectory(WorkspaceApplicationService workspaces) {
        return new ModelingWorkspaceDirectory(workspaces);
    }

    @Bean
    LlmPort llmPort(AiSettings settings, MeterRegistry meters) {
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers).bindTo(meters);
        return new AnthropicLlmClient(settings, breakers);
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
    AdvisorKernel advisorKernel(UnitOfWork advisorUnitOfWork, AdvisorEvents events, AdvisorMetrics metrics,
                                Clock clock) {
        return new AdvisorKernel(advisorUnitOfWork, events, metrics, clock);
    }

    @Bean
    AdvisorBudgets advisorBudgets(AdvisorKernel kernel, AdvisorSettingsRepository settings,
                                  TokenBudgetRepository budgets, AdvisorPolicy policy) {
        return new AdvisorBudgets(kernel, settings, budgets, policy);
    }

    @Bean
    AdvisorApplicationService advisorApplicationService(AdvisorKernel kernel, AdvisorBudgets budgets,
                                                        ModelContextReader reader, MetamodelCatalog metamodel,
                                                        AdvisorSessionRepository sessions, LlmPort llm,
                                                        PromptCatalog prompts, AdvisorPolicy policy, Clock clock) {
        return new AdvisorApplicationServiceImpl(new AdvisorSessionStartCommandHandler(kernel, budgets, reader,
                metamodel, sessions, llm, prompts, policy, new RateLimiter(clock), new ActiveSessions()));
    }

    @Bean
    AdvisorAdminApplicationService advisorAdminApplicationService(AdvisorKernel kernel, AdvisorBudgets budgets,
                                                                  WorkspaceDirectory workspaces,
                                                                  AdvisorSettingsRepository settings,
                                                                  AuditLog audit) {
        return new AdvisorAdminApplicationServiceImpl(new AdvisorStatusGetQueryHandler(kernel, budgets, workspaces),
                new AdvisorSettingsGetQueryHandler(kernel, budgets, workspaces),
                new AdvisorSettingsConfigureCommandHandler(kernel, budgets, settings, workspaces),
                new TokenUsageGetQueryHandler(kernel, budgets, workspaces, audit));
    }
}
