package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotArchive;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotReader;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotWriter;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Общее у сценариев версий: найти версию, достать её содержимое, заменить им модель,
 * собрать текущее содержимое тем же снимком, что и сохранение.
 */
public final class VersionContents {

    private final ModelingKernel kernel;
    private final VersionCommitter committer;
    private final SnapshotWriter snapshotWriter;
    private final SnapshotReader snapshotReader;
    private final SnapshotArchive archive;

    public VersionContents(ModelingKernel kernel, VersionCommitter committer, SnapshotWriter snapshotWriter,
                           SnapshotReader snapshotReader, SnapshotArchive archive) {
        this.kernel = kernel;
        this.committer = committer;
        this.snapshotWriter = snapshotWriter;
        this.snapshotReader = snapshotReader;
        this.archive = archive;
    }

    /** Итог замены содержимого: версия и сколько объектов в новом содержимом непрозрачны. */
    public record Replacement(ModelVersion version, int opaqueObjects) {
    }

    /** Версия модели по номеру; нет такой — «не найдено». */
    public ModelVersion requireVersion(ModelId modelId, long versionNo) {
        return kernel.versions().find(modelId, versionNo)
                .orElseThrow(() -> ModelingDomainException.notFound(
                        Message.of(ModelingMessages.VERSION_OF_MODEL, versionNo, modelId)));
    }

    /**
     * Содержимое версии: снимок из базы, а очищенный — из коммита версии. Вернуть
     * неоткуда — {@code 410}. Зовётся вне транзакции: восстановление из коммита ходит
     * в репозиторий по сети.
     */
    public byte[] content(ModelHeader header, ModelVersion version) {
        if (version.hasSnapshot()) {
            return Snapshots.gunzip(version.requireSnapshot());
        }
        Optional<byte[]> restored = version.gitSha() != null ? archive.restore(header, version) : Optional.empty();
        return restored.orElseGet(() -> Snapshots.gunzip(version.requireSnapshot()));
    }

    /**
     * Содержимое модели заменяется снимком целиком и фиксируется версией — в транзакции
     * вызывающего. Общая часть отката к версии и импорта состояния извне.
     */
    public Replacement replace(ArchitectureModel model, byte[] xml, EditorIdentity actor, String comment) {
        ModelContent restored = snapshotReader.read(xml, model.header(), kernel.now());
        kernel.models().replaceContent(restored);
        return new Replacement(committer.commit(restored.model(), actor, comment), restored.model().opaqueCount());
    }

    /** Текущее содержимое модели в базе — тем же снимком, который соберёт сохранение. */
    public byte[] currentXml(ModelId modelId) {
        return snapshotWriter.write(new ModelContent(kernel.models().load(modelId).orElseThrow(),
                kernel.views().loadAll(modelId)));
    }
}
