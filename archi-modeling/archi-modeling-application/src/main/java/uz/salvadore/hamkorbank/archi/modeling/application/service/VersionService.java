package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.port.GitBinding;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotArchive;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotReader;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotWriter;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelVersionCommitted;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.SnapshotRetention;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.VersionId;

/**
 * Сохранить модель как версию, пометить версию, откатиться к версии;
 * снимки для экспорта и их ретеншен.
 */
public final class VersionService {

    /** Код отказа выгрузки удалённой модели. */
    public static final String MODEL_DELETED = ModelingCodes.MODEL_DELETED;

    private final ModelingKernel kernel;
    private final SnapshotWriter snapshotWriter;
    private final SnapshotReader snapshotReader;
    private final GitBinding gitBinding;
    private final SnapshotArchive archive;
    private final SnapshotRetention retention;
    private final boolean retentionEnabled;

    public VersionService(ModelingKernel kernel, SnapshotWriter snapshotWriter, SnapshotReader snapshotReader,
                          GitBinding gitBinding, SnapshotArchive archive, ZoneId zone, boolean retentionEnabled) {
        this.kernel = kernel;
        this.snapshotWriter = snapshotWriter;
        this.snapshotReader = snapshotReader;
        this.gitBinding = gitBinding;
        this.archive = archive;
        this.retention = new SnapshotRetention(zone);
        this.retentionEnabled = retentionEnabled;
    }

    /** Итог сохранения: версия и признак, создана ли она сейчас. */
    public record SaveResult(ModelVersion version, boolean created) {
    }

    /**
     * Сохранение. Изменений с последней версии нет — версия не создаётся:
     * журнал версий — не журнал нажатий. Повтор с тем же ключом отдаёт первую версию.
     */
    public SaveResult save(EditorIdentity actor, ModelId modelId, Optional<String> comment,
                           Optional<String> idempotencyKey) {
        return kernel.run(Operation.SAVE_MODEL, actor, () -> kernel.unitOfWork.write(() -> {
            boolean[] created = {false};
            ModelVersion version = kernel.idempotent("SaveModel", actor, idempotencyKey,
                    IdempotentCommand.fingerprint(modelId, comment),
                    () -> {
                        ArchitectureModel model = kernel.writableModel(modelId, actor);
                        Optional<Long> before = kernel.versions.last(modelId).map(ModelVersion::versionNo);
                        ModelVersion committed = commit(model, actor, comment);
                        created[0] = before.map(no -> committed.versionNo() > no).orElse(true);
                        return String.valueOf(committed.versionNo());
                    },
                    no -> kernel.versions.find(modelId, Long.parseLong(no)).orElseThrow());
            return new SaveResult(version, created[0]);
        }));
    }

    /**
     * Версия из текущего содержимого базы. Тот же отпечаток, что у последней версии, —
     * новая не пишется и возвращается последняя. Снимок собирается из базы, а не из
     * того, что держит вызывающий: так он проверяет и хранение.
     */
    ModelVersion commit(ArchitectureModel model, EditorIdentity actor, Optional<String> comment) {
        ModelContent content = new ModelContent(kernel.models.load(model.id()).orElseThrow(),
                kernel.views.loadAll(model.id()));
        byte[] xml = snapshotWriter.write(content);
        String hash = Snapshots.sha256(xml);
        Optional<ModelVersion> last = kernel.versions.last(model.id());
        if (last.isPresent() && last.get().contentHash().equals(hash)) {
            return last.get();
        }
        Instant now = kernel.now();
        ModelVersion version = ModelVersion.next(last, VersionId.next(kernel.uuids), model.id(), actor.subject(),
                comment, now, Snapshots.gzip(xml), hash);
        kernel.versions.save(version);
        kernel.events.publish(List.of(new ModelVersionCommitted(model.id(), model.workspaceId(), version.versionNo(),
                actor.subject(), version.comment(), content.model().elements().size(),
                content.model().relationships().size(), now)));
        return version;
    }

    public List<ModelVersion> list(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.LIST_VERSIONS, actor, () -> kernel.unitOfWork.read(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            return kernel.versions.list(modelId);
        }));
    }

    /** Метка релиза. Содержимого модели не меняет, поэтому блокировки не требует. */
    public ModelVersion label(EditorIdentity actor, ModelId modelId, long versionNo, Optional<String> label) {
        return kernel.run(Operation.LABEL_VERSION, actor, () -> kernel.unitOfWork.write(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.WRITE);
            ModelVersion labelled = requireVersion(modelId, versionNo).labelled(label);
            kernel.versions.save(labelled);
            return labelled;
        }));
    }

    /**
     * Откат: содержимое версии становится текущим и фиксируется новой версией — история
     * не переписывается, а дописывается.
     */
    public ModelVersion rollback(EditorIdentity actor, ModelId modelId, long versionNo, Optional<String> comment) {
        return kernel.run(Operation.RESTORE_VERSION, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(modelId, actor);
            byte[] xml = content(model.header(), requireVersion(modelId, versionNo));
            return replace(model, xml, actor, comment.filter(c -> !c.isBlank()).orElseGet(
                    () -> kernel.texts.text(Message.of(ModelingMessages.COMMENT_ROLLBACK, versionNo)))).version();
        }));
    }

    /** Итог замены содержимого: версия и сколько объектов в новом содержимом непрозрачны. */
    record Replacement(ModelVersion version, int opaqueObjects) {
    }

    /**
     * Содержимое модели заменяется снимком целиком и фиксируется версией — в транзакции
     * вызывающего. Общая часть отката к версии и импорта состояния извне.
     */
    Replacement replace(ArchitectureModel model, byte[] xml, EditorIdentity actor, String comment) {
        ModelContent restored = snapshotReader.read(xml, model.header(), kernel.now());
        kernel.models.replaceContent(restored);
        int opaque = (int) (restored.model().elements().values().stream().filter(e -> !e.supported()).count()
                + restored.model().relationships().values().stream().filter(r -> !r.supported()).count());
        return new Replacement(commit(restored.model(), actor, Optional.of(comment)), opaque);
    }

    /** Текущее содержимое модели в базе — тем же снимком, который соберёт сохранение. */
    byte[] currentXml(ModelId modelId) {
        return snapshotWriter.write(new ModelContent(kernel.models.load(modelId).orElseThrow(),
                kernel.views.loadAll(modelId)));
    }

    /**
     * Снимок версии для выгрузки: указанной или последней.
     * Удалённая модель не выгружается ({@code 409}); очищенный снимок возвращается из Git,
     * а без Git — {@code 410}.
     */
    public VersionSnapshot snapshot(EditorIdentity actor, ModelId modelId, Optional<Long> versionNo) {
        return kernel.unitOfWork.read(() -> {
            ModelHeader header = kernel.visibleHeader(modelId, actor, AclAccess.READ);
            if (header.status() != ModelStatus.ACTIVE) {
                throw new ModelingException(MODEL_DELETED, Failure.CONFLICT, Message.of(ModelingMessages.MODEL_DELETED));
            }
            ModelVersion version = versionNo.map(no -> requireVersion(modelId, no))
                    .orElseGet(() -> kernel.versions.last(modelId)
                            .orElseThrow(() -> ModelingException.notFound(
                                    Message.of(ModelingMessages.MODEL_VERSION, modelId))));
            return new VersionSnapshot(header, version.versionNo(), content(header, version));
        });
    }

    /**
     * Содержимое версии: снимок из базы, а очищенный — из коммита версии. Вернуть
     * неоткуда — {@code 410}, как и раньше.
     */
    private byte[] content(ModelHeader header, ModelVersion version) {
        if (version.snapshot().isPresent()) {
            return Snapshots.gunzip(version.requireSnapshot());
        }
        Optional<byte[]> restored = version.gitSha().isPresent() ? archive.restore(header, version) : Optional.empty();
        return restored.orElseGet(() -> Snapshots.gunzip(version.requireSnapshot()));
    }

    /**
     * Зафиксированная версия для публикации в Git — системное чтение, без актора:
     * публикует фоновый обработчик. Модели или версии нет, снимок очищен — пусто.
     */
    public Optional<CommittedVersion> committed(ModelId modelId, long versionNo) {
        return kernel.unitOfWork.read(() -> kernel.models.findHeader(modelId).flatMap(header ->
                kernel.versions.find(modelId, versionNo).filter(v -> v.snapshot().isPresent())
                        .map(v -> new CommittedVersion(header, v, Snapshots.gunzip(v.requireSnapshot())))));
    }

    /** Последняя опубликованная в Git версия модели. */
    public Optional<ModelVersion> lastPublished(ModelId modelId) {
        return kernel.unitOfWork.read(() -> kernel.versions.list(modelId).stream()
                .filter(v -> v.gitSha().isPresent()).findFirst());
    }

    /** Связать версию с коммитом, в котором её содержимое лежит в репозитории. */
    public void recordGitSha(ModelId modelId, long versionNo, String sha) {
        kernel.unitOfWork.write(() -> {
            kernel.versions.save(requireVersion(modelId, versionNo).publishedAs(sha));
            return null;
        });
    }

    /** Модель видна актору — её рабочее пространство; иначе «не найдено», как везде. */
    public ModelHeader visibleModel(EditorIdentity actor, ModelId modelId) {
        return kernel.unitOfWork.read(() -> kernel.visibleHeader(modelId, actor, AclAccess.READ));
    }

    /**
     * Очистка снимков по правилу ретеншена. Не трогает пространство без настроенного
     * и доступного репозитория: там снимок — единственная копия.
     *
     * @return сколько снимков очищено
     */
    public int purgeSnapshots() {
        if (!retentionEnabled) {
            return 0;
        }
        int purged = 0;
        for (ModelId modelId : kernel.unitOfWork.read(kernel.versions::modelsWithSnapshots)) {
            purged += kernel.unitOfWork.write(() -> {
                ModelHeader header = kernel.models.findHeader(modelId).orElseThrow();
                boolean gitBound = gitBinding.boundAndReachable(header.workspaceId());
                List<ModelVersion> purgeable = retention.purgeable(kernel.versions.list(modelId), kernel.now(),
                        gitBound);
                purgeable.forEach(v -> kernel.versions.save(v.snapshotPurged(gitBound)));
                return purgeable.size();
            });
        }
        return purged;
    }

    private ModelVersion requireVersion(ModelId modelId, long versionNo) {
        return kernel.versions.find(modelId, versionNo)
                .orElseThrow(() -> ModelingException.notFound(
                        Message.of(ModelingMessages.VERSION_OF_MODEL, versionNo, modelId)));
    }
}
