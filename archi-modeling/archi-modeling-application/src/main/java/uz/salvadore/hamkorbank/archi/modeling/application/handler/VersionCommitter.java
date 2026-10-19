package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotWriter;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.VersionId;

/**
 * Фиксация версии из текущего содержимого базы — в транзакции вызывающего. Общая часть
 * сохранения, создания модели, отката и импорта.
 */
public final class VersionCommitter {

    private final ModelingKernel kernel;
    private final SnapshotWriter snapshotWriter;

    public VersionCommitter(ModelingKernel kernel, SnapshotWriter snapshotWriter) {
        this.kernel = kernel;
        this.snapshotWriter = snapshotWriter;
    }

    /**
     * Версия из текущего содержимого базы. Тот же отпечаток, что у последней версии, —
     * новая не пишется и возвращается последняя. Снимок собирается из базы, а не из
     * того, что держит вызывающий: так он проверяет и хранение. {@code null} — без комментария.
     */
    public ModelVersion commit(ArchitectureModel model, EditorIdentity actor, String comment) {
        ModelContent content = new ModelContent(kernel.models().load(model.id()).orElseThrow(),
                kernel.views().loadAll(model.id()));
        byte[] xml = snapshotWriter.write(content);
        String hash = Snapshots.sha256(xml);
        Optional<ModelVersion> last = kernel.versions().last(model.id());
        if (last.isPresent() && last.get().contentHash().equals(hash)) {
            return last.get();
        }
        Instant now = kernel.now();
        ModelVersion.Committed committed = ModelVersion.commit(last.orElse(null), VersionId.next(kernel.uuids()),
                model.id(), model.workspaceId(), actor.subject(), comment, now, Snapshots.gzip(xml), hash,
                new ModelVersion.ModelSize(content.model().elements().size(),
                        content.model().relationships().size()));
        kernel.versions().save(committed.version());
        kernel.events().publish(List.of(committed.event()));
        return committed.version();
    }

    /** Текущее содержимое модели в базе — тем же снимком, который соберёт сохранение. */
    public byte[] currentXml(ArchitectureModel model) {
        return snapshotWriter.write(new ModelContent(kernel.models().load(model.id()).orElseThrow(),
                kernel.views().loadAll(model.id())));
    }
}
