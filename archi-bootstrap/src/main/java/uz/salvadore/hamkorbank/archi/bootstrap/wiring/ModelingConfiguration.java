package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.transaction.PlatformTransactionManager;
import uz.salvadore.hamkorbank.archi.bootstrap.git.GitSnapshotArchive;
import uz.salvadore.hamkorbank.archi.interchange.application.mapping.ArchimateSnapshots;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitPublishingApplicationService;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EnsureDefaultWorkspaceCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.AccessListGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.AccessListReplaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.CommittedVersionGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ElementTypeListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.FolderCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.LockAcquireCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.LockGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.LockReleaseCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelContentGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelContentReplaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelContents;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelImportCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelPurgeCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelRestoreCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelRowsVerifyQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelSummaryGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelValidateQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.PublishedVersionGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationTypeSuggestQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationshipCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationshipDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.RelationshipEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.SnapshotPurgeCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.TreeItemRenameCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.TreeItemsDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.TreeItemsMoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionCommitter;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionContents;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionGitShaRecordCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionLabelCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionRestoreCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionSaveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionSnapshotGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEdgeEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEdgePlaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewEdgeRemoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewLayoutProposeQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewLayoutSaveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewLookup;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodeEditCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodeMoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodePlaceCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewNodeRemoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.WorkspaceEnsureDefaultCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.WorkspaceListQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.WorkspaceResolveQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.DomainEventPublisher;
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
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.AccessListApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ElementApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.LockApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.MetamodelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.RelationshipApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.SnapshotRetentionApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.TreeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.VersionApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.AccessListApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ElementApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.LockApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.MetamodelApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelExchangeApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.RelationshipApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.SnapshotRetentionApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.TreeApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ViewApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ViewQueryApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.application.service.WorkspaceApplicationServiceImpl;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;

/** Сценарии modeling — бинами, порты — реализациями из адаптеров и отсюда. */
@Configuration(proxyBeanMethods = false)
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

    /** Зафиксированная версия — ещё и в outbox, той же транзакцией: её ждёт выгрузка в Git. */
    @Bean
    DomainEventPublisher modelingEvents(AfterCommitEventPublisher publisher, GitOutbox outbox, Clock clock) {
        return new OutboxEventPublisher(publisher, outbox, clock);
    }

    @Bean
    UseCaseMetrics modelingMetrics(MeterRegistry registry) {
        return new ModelingMicrometerMetrics(registry);
    }

    /** Доступность репозитория и восстановление очищенного снимка — через публикацию в Git. */
    @Bean
    GitSnapshotArchive gitSnapshotArchive(ObjectProvider<GitPublishingApplicationService> publishing) {
        return new GitSnapshotArchive(publishing::getObject);
    }

    @Bean
    ArchimateSnapshots archimateSnapshots(Clock clock) {
        return new ArchimateSnapshots(new StaxArchiDocumentReader(), new ArchiXmlWriter(), new UuidV7(clock)::next);
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
    VersionCommitter versionCommitter(ModelingKernel kernel, ArchimateSnapshots snapshots) {
        return new VersionCommitter(kernel, snapshots);
    }

    @Bean
    VersionContents versionContents(ModelingKernel kernel, VersionCommitter committer, ArchimateSnapshots snapshots,
                                    GitSnapshotArchive git) {
        return new VersionContents(kernel, committer, snapshots, snapshots, git);
    }

    @Bean
    VersionApplicationService versionApplicationService(ModelingKernel kernel, VersionCommitter committer,
                                                        VersionContents contents) {
        return new VersionApplicationServiceImpl(new VersionSaveCommandHandler(kernel, committer),
                new VersionListQueryHandler(kernel), new VersionLabelCommandHandler(kernel, contents),
                new VersionRestoreCommandHandler(kernel, contents));
    }

    /** Вход для выгрузки и Git: роль у каждой их операции своя, её проверяет вызывающий. */
    @Bean
    ModelExchangeApplicationService modelExchangeApplicationService(ModelingKernel kernel,
                                                                    WorkspaceRepository workspaces,
                                                                    VersionCommitter committer,
                                                                    VersionContents contents) {
        return new ModelExchangeApplicationServiceImpl(new ModelImportCommandHandler(kernel, workspaces, committer),
                new ModelRowsVerifyQueryHandler(kernel),
                new VersionSnapshotGetQueryHandler(kernel, contents),
                new ModelSummaryGetQueryHandler(kernel), new ModelContentGetQueryHandler(kernel, contents),
                new ModelContentReplaceCommandHandler(kernel, contents), new CommittedVersionGetQueryHandler(kernel),
                new PublishedVersionGetQueryHandler(kernel), new VersionGitShaRecordCommandHandler(kernel, contents));
    }

    @Bean
    SnapshotRetentionApplicationService snapshotRetentionApplicationService(ModelingKernel kernel,
                                                                            GitSnapshotArchive git,
                                                                            ModelingSettings settings) {
        return new SnapshotRetentionApplicationServiceImpl(new SnapshotPurgeCommandHandler(kernel, git,
                ZoneId.systemDefault(), settings.retentionEnabled()));
    }

    @Bean
    ModelApplicationService modelApplicationService(ModelingKernel kernel, WorkspaceRepository workspaces,
                                                    UnitOfWork unitOfWork, VersionCommitter committer) {
        return new ModelApplicationServiceImpl(new ModelCreateCommandHandler(kernel, workspaces,
                new WorkspaceResolveQueryHandler(workspaces, unitOfWork), committer),
                new ModelEditCommandHandler(kernel),
                new ModelDeleteCommandHandler(kernel), new ModelRestoreCommandHandler(kernel),
                new ModelPurgeCommandHandler(kernel));
    }

    @Bean
    ModelQueryApplicationService modelQueryApplicationService(ModelingKernel kernel, WorkspaceRepository workspaces,
                                                              UnitOfWork unitOfWork) {
        return new ModelQueryApplicationServiceImpl(new ModelListQueryHandler(kernel,
                new WorkspaceResolveQueryHandler(workspaces, unitOfWork)), new ModelGetQueryHandler(kernel),
                new ModelValidateQueryHandler(kernel));
    }

    @Bean
    ViewQueryApplicationService viewQueryApplicationService(ModelingKernel kernel) {
        ViewLookup lookup = new ViewLookup(kernel);
        return new ViewQueryApplicationServiceImpl(new ViewGetQueryHandler(kernel, lookup),
                new ViewLayoutProposeQueryHandler(kernel, lookup));
    }

    @Bean
    LockApplicationService lockApplicationService(ModelingKernel kernel) {
        return new LockApplicationServiceImpl(new LockAcquireCommandHandler(kernel),
                new LockReleaseCommandHandler(kernel), new LockGetQueryHandler(kernel));
    }

    @Bean
    ElementApplicationService elementApplicationService(ModelingKernel kernel) {
        ModelContents contents = new ModelContents(kernel);
        return new ElementApplicationServiceImpl(new ElementCreateCommandHandler(kernel),
                new ElementEditCommandHandler(kernel, contents), new ElementDeleteCommandHandler(kernel, contents));
    }

    @Bean
    RelationshipApplicationService relationshipApplicationService(ModelingKernel kernel) {
        ModelContents contents = new ModelContents(kernel);
        return new RelationshipApplicationServiceImpl(new RelationshipCreateCommandHandler(kernel),
                new RelationshipEditCommandHandler(kernel, contents),
                new RelationshipDeleteCommandHandler(kernel, contents));
    }

    @Bean
    MetamodelApplicationService metamodelApplicationService() {
        return new MetamodelApplicationServiceImpl(new ElementTypeListQueryHandler(),
                new RelationTypeSuggestQueryHandler());
    }

    @Bean
    ViewApplicationService viewApplicationService(ModelingKernel kernel) {
        ViewLookup lookup = new ViewLookup(kernel);
        return new ViewApplicationServiceImpl(new ViewCreateCommandHandler(kernel),
                new ViewNodePlaceCommandHandler(kernel, lookup), new ViewEdgePlaceCommandHandler(kernel, lookup),
                new ViewNodeMoveCommandHandler(kernel, lookup), new ViewNodeEditCommandHandler(kernel, lookup),
                new ViewEdgeEditCommandHandler(kernel, lookup), new ViewNodeRemoveCommandHandler(kernel, lookup),
                new ViewEdgeRemoveCommandHandler(kernel, lookup), new ViewLayoutSaveCommandHandler(kernel, lookup));
    }

    @Bean
    TreeApplicationService treeApplicationService(ModelingKernel kernel) {
        return new TreeApplicationServiceImpl(new FolderCreateCommandHandler(kernel),
                new TreeItemRenameCommandHandler(kernel), new TreeItemsMoveCommandHandler(kernel),
                new TreeItemsDeleteCommandHandler(kernel, new ModelContents(kernel)));
    }

    @Bean
    AccessListApplicationService accessListApplicationService(ModelingKernel kernel) {
        return new AccessListApplicationServiceImpl(new AccessListGetQueryHandler(kernel),
                new AccessListReplaceCommandHandler(kernel));
    }

    @Bean
    WorkspaceApplicationService workspaceApplicationService(WorkspaceRepository workspaces, UnitOfWork unitOfWork,
                                                            TextCatalog texts, Clock clock) {
        return new WorkspaceApplicationServiceImpl(new WorkspaceListQueryHandler(workspaces, unitOfWork),
                new WorkspaceResolveQueryHandler(workspaces, unitOfWork),
                new WorkspaceEnsureDefaultCommandHandler(workspaces, unitOfWork, texts, clock));
    }

    /**
     * Первое рабочее пространство — при старте на пустой базе. Запроса
     * на старте нет, поэтому язык имени — язык приложения по умолчанию, а не системная
     * локаль сервера.
     */
    @Bean
    ApplicationRunner defaultWorkspace(WorkspaceApplicationService workspaces, ModelingSettings settings,
                                       WebProperties web) {
        return args -> {
            LocaleContextHolder.setLocale(Optional.ofNullable(web.getLocale()).orElse(Locale.ROOT));
            try {
                workspaces.ensureDefaultWorkspace(new EnsureDefaultWorkspaceCommand(
                        Optional.ofNullable(settings.defaultWorkspaceName()).filter(name -> !name.isBlank())
                                .orElse(null)));
            } finally {
                LocaleContextHolder.resetLocaleContext();
            }
        };
    }

    /** Очистка снимков по расписанию; выключенная настройкой — пустой проход. */
    @Bean
    SchedulingConfigurer snapshotRetention(SnapshotRetentionApplicationService retention,
                                           ModelingSettings settings) {
        return registrar -> registrar.addCronTask(retention::purgeSnapshots, settings.retentionCron());
    }
}
