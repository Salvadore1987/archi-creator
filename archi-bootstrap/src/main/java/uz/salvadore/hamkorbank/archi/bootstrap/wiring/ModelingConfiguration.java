package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.transaction.PlatformTransactionManager;
import uz.salvadore.hamkorbank.archi.interchange.application.mapping.ArchimateSnapshots;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.modeling.application.port.DomainEventPublisher;
import uz.salvadore.hamkorbank.archi.modeling.application.port.GitBinding;
import uz.salvadore.hamkorbank.archi.modeling.application.port.IdempotencyRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelAccessListRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelLockRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelVersionRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UseCaseMetrics;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.service.AccessListService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ElementService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.LockService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelImportService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelLifecycleService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.application.service.RelationshipService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.TreeService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ViewService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.WorkspaceService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiIdGenerator;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Сценарии modeling — бинами, порты — реализациями из адаптеров и отсюда. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ModelingSettings.class)
public class ModelingConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TransactionalUnitOfWork unitOfWork(PlatformTransactionManager transactions) {
        return new TransactionalUnitOfWork(transactions);
    }

    @Bean
    AfterCommitEventPublisher afterCommitEventPublisher(ApplicationEventPublisher publisher) {
        return new AfterCommitEventPublisher(publisher);
    }

    @Bean
    DomainEventPublisher modelingEvents(AfterCommitEventPublisher publisher) {
        return publisher::publish;
    }

    @Bean
    UseCaseMetrics modelingMetrics(MeterRegistry registry) {
        return new ModelingMicrometerMetrics(registry);
    }

    /** До этапа 7a Git не настроен ни у кого: снимки не чистятся. */
    @Bean
    GitBinding gitBinding() {
        return workspaceId -> false;
    }

    @Bean
    ArchimateSnapshots archimateSnapshots(Clock clock) {
        UuidV7 uuids = new UuidV7(clock);
        ArchiIdGenerator archiIds = new ArchiIdGenerator();
        return new ArchimateSnapshots(new StaxArchiDocumentReader(), new ArchiXmlWriter(), uuids::next,
                archiIds::next);
    }

    @Bean
    ModelingKernel modelingKernel(ModelRepository models, ViewRepository views, ModelLockRepository locks,
                                  ModelVersionRepository versions, ModelAccessListRepository accessLists,
                                  IdempotencyRepository idempotency, UnitOfWork unitOfWork,
                                  DomainEventPublisher events, UseCaseMetrics metrics, TextCatalog texts, Clock clock,
                                  ModelingSettings settings) {
        return new ModelingKernel(models, views, locks, versions, accessLists, idempotency, unitOfWork, events,
                metrics, texts, clock, settings.lockTtl());
    }

    @Bean
    VersionService versionService(ModelingKernel kernel, ArchimateSnapshots snapshots, GitBinding gitBinding,
                                  ModelingSettings settings) {
        return new VersionService(kernel, snapshots, snapshots, gitBinding, ZoneId.systemDefault(),
                settings.retentionEnabled());
    }

    @Bean
    ModelLifecycleService modelLifecycleService(ModelingKernel kernel, WorkspaceRepository workspaces,
                                                VersionService versions) {
        return new ModelLifecycleService(kernel, workspaces, versions);
    }

    @Bean
    ModelQueryService modelQueryService(ModelingKernel kernel) {
        return new ModelQueryService(kernel);
    }

    @Bean
    LockService lockService(ModelingKernel kernel) {
        return new LockService(kernel);
    }

    @Bean
    ElementService elementService(ModelingKernel kernel) {
        return new ElementService(kernel);
    }

    @Bean
    RelationshipService relationshipService(ModelingKernel kernel, ElementService elements) {
        return new RelationshipService(kernel, elements);
    }

    @Bean
    ViewService viewService(ModelingKernel kernel) {
        return new ViewService(kernel);
    }

    @Bean
    TreeService treeService(ModelingKernel kernel, ElementService elements, RelationshipService relationships) {
        return new TreeService(kernel, elements, relationships);
    }

    @Bean
    AccessListService accessListService(ModelingKernel kernel) {
        return new AccessListService(kernel);
    }

    @Bean
    ModelImportService modelImportService(ModelingKernel kernel, WorkspaceRepository workspaces,
                                          VersionService versions) {
        return new ModelImportService(kernel, workspaces, versions);
    }

    @Bean
    WorkspaceService workspaceService(WorkspaceRepository workspaces, UnitOfWork unitOfWork, Clock clock) {
        return new WorkspaceService(workspaces, unitOfWork, clock);
    }

    /** Первое рабочее пространство — при старте на пустой базе (журнал этапа 0). */
    @Bean
    ApplicationRunner defaultWorkspace(WorkspaceService workspaces, ModelingSettings settings) {
        return args -> workspaces.ensureDefault(settings.defaultWorkspaceName());
    }

    /** Очистка снимков по расписанию; выключенная настройкой — пустой проход. */
    @Bean
    SchedulingConfigurer snapshotRetention(VersionService versions, ModelingSettings settings) {
        return registrar -> registrar.addCronTask(versions::purgeSnapshots, settings.retentionCron());
    }
}
