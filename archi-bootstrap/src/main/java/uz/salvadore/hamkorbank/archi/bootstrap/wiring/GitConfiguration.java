package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import java.nio.file.Path;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import uz.salvadore.hamkorbank.archi.bootstrap.git.EnvironmentSecretResolver;
import uz.salvadore.hamkorbank.archi.bootstrap.git.GitSettings;
import uz.salvadore.hamkorbank.archi.bootstrap.git.JGitRepository;
import uz.salvadore.hamkorbank.archi.bootstrap.git.ModelingModelStates;
import uz.salvadore.hamkorbank.archi.bootstrap.git.ModelingPublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitMerge;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublisher;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPull;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitBindingConfigureCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitBindingDisableCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitBindingGetQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitOutboxPublishCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitReachabilityQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitSnapshotRestoreQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.InterchangeKernel;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelBranchMergeCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelBranchMergePreviewQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelGitPullCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelGitPullPreviewQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelGitPushCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelMerges;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.port.SecretResolver;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitBindingApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitMergeApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitPublishingApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitPullApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.application.service.GitBindingApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.interchange.application.service.GitMergeApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.interchange.application.service.GitPublishingApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.interchange.application.service.GitPullApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;

/** Git: адаптеры портов, публикация с проходом очереди по расписанию и импорт по ссылке. */
@Configuration
@EnableConfigurationProperties(GitSettings.class)
public class GitConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(GitConfiguration.class);

    @Bean
    GitRepositoryPort gitRepository(GitSettings settings) {
        Path cache = settings.cacheDir() != null ? settings.cacheDir()
                : Path.of(System.getProperty("java.io.tmpdir"), "archi-git");
        return new JGitRepository(cache, settings.username(), settings.timeout());
    }

    @Bean
    SecretResolver secretResolver(Environment environment) {
        return new EnvironmentSecretResolver(environment);
    }

    @Bean
    PublishedVersions publishedVersions(ModelExchangeApplicationService models) {
        return new ModelingPublishedVersions(models);
    }

    @Bean
    GitPublisher gitPublisher(GitOutbox outbox, GitBindingRepository bindings,
                                              PublishedVersions versions, ModelMerges merges,
                                              GitRepositoryPort repository, SecretResolver secrets,
                                              UnitOfWork unitOfWork, InterchangeEvents events,
                                              InterchangeMetrics metrics, TextCatalog texts, Clock clock,
                                              GitSettings settings) {
        return new GitPublisher(outbox, bindings, versions, merges, repository, secrets, unitOfWork, events,
                metrics, texts, clock, new GitPublisher.Settings(settings.committerName(),
                settings.committerEmail(), settings.authorEmailDomain(), settings.retryBase(), settings.retryMax(),
                settings.outboxRetention()));
    }

    @Bean
    ModelStates modelStates(ModelExchangeApplicationService models) {
        return new ModelingModelStates(models);
    }

    @Bean
    GitPull gitPull(GitBindingRepository bindings, GitRepositoryPort repository,
                                      SecretResolver secrets, ModelStates states, ImportSessionRepository sessions,
                                      ImportPolicy policy, GitOutbox outbox, PublishedVersions versions,
                                      UnitOfWork unitOfWork, InterchangeEvents events, InterchangeMetrics metrics,
                                      TextCatalog texts, Clock clock) {
        return new GitPull(bindings, repository, secrets, states, sessions, policy, outbox, versions,
                unitOfWork, events, metrics, texts, clock);
    }

    @Bean
    GitMerge gitMerge(GitBindingRepository bindings, GitRepositoryPort repository,
                                    SecretResolver secrets, ModelStates states, ImportSessionRepository sessions,
                                    ImportPolicy policy, GitOutbox outbox, PublishedVersions versions,
                                    ModelMerges merges, UnitOfWork unitOfWork, InterchangeEvents events,
                                    InterchangeMetrics metrics, TextCatalog texts, Clock clock) {
        return new GitMerge(bindings, repository, secrets, states, sessions, policy, outbox, versions, merges,
                unitOfWork, events, metrics, texts, clock);
    }

    @Bean
    GitPublishingApplicationService gitPublishingApplicationService(InterchangeKernel kernel, GitPublisher publisher) {
        return new GitPublishingApplicationServiceImpl(new ModelGitPushCommandHandler(kernel, publisher),
                new GitOutboxPublishCommandHandler(publisher), new GitReachabilityQueryHandler(publisher),
                new GitSnapshotRestoreQueryHandler(publisher));
    }

    @Bean
    GitPullApplicationService gitPullApplicationService(InterchangeKernel kernel, GitPull pull) {
        return new GitPullApplicationServiceImpl(new ModelGitPullPreviewQueryHandler(kernel, pull),
                new ModelGitPullCommandHandler(kernel, pull));
    }

    @Bean
    GitMergeApplicationService gitMergeApplicationService(InterchangeKernel kernel, GitMerge merge) {
        return new GitMergeApplicationServiceImpl(new ModelBranchMergePreviewQueryHandler(kernel, merge),
                new ModelBranchMergeCommandHandler(kernel, merge));
    }

    @Bean
    GitBindingApplicationService gitBindingApplicationService(InterchangeKernel kernel, GitBindingRepository bindings,
                                                              GitSettings settings) {
        return new GitBindingApplicationServiceImpl(new GitBindingGetQueryHandler(kernel, bindings),
                new GitBindingConfigureCommandHandler(kernel, bindings, settings.allowLocalRepositories()),
                new GitBindingDisableCommandHandler(kernel, bindings));
    }

    /** Проход очереди: отказ одного прохода пишется в журнал, расписание не останавливается. */
    @Bean
    SchedulingConfigurer gitPublishing(GitPublishingApplicationService publishing, GitSettings settings) {
        return registrar -> registrar.addFixedDelayTask(() -> {
            try {
                publishing.publishPendingVersions();
            } catch (RuntimeException e) { // NOPMD - сбой одного прохода не останавливает расписание
                LOG.warn("git publishing pass failed", e);
            }
        }, settings.pollInterval());
    }
}
