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
import uz.salvadore.hamkorbank.archi.bootstrap.git.ModelingPublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublishingService;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.port.SecretResolver;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;

/** Выгрузка в Git: адаптеры портов, сценарий публикации и проход очереди по расписанию. */
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
    PublishedVersions publishedVersions(VersionService versions) {
        return new ModelingPublishedVersions(versions);
    }

    @Bean
    GitPublishingService gitPublishingService(GitOutbox outbox, GitBindingRepository bindings,
                                              PublishedVersions versions, GitRepositoryPort repository,
                                              SecretResolver secrets, UnitOfWork unitOfWork, InterchangeEvents events,
                                              InterchangeMetrics metrics, TextCatalog texts, Clock clock,
                                              GitSettings settings) {
        return new GitPublishingService(outbox, bindings, versions, repository, secrets, unitOfWork, events, metrics,
                texts, clock, new GitPublishingService.Settings(settings.committerName(), settings.committerEmail(),
                settings.authorEmailDomain(), settings.retryBase(), settings.retryMax()));
    }

    /** Проход очереди: отказ одного прохода пишется в журнал, расписание не останавливается. */
    @Bean
    SchedulingConfigurer gitPublishing(GitPublishingService publishing, GitSettings settings) {
        return registrar -> registrar.addFixedDelayTask(() -> {
            try {
                publishing.publishPending();
            } catch (RuntimeException e) {
                LOG.warn("git publishing pass failed", e);
            }
        }, settings.pollInterval());
    }
}
