package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.Arrays;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceModelContentCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ReplaceModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Содержимое модели заменяется снимком и фиксируется новой версией — одной транзакцией
 * с вызывающим. Нужна блокировка автора; роль проверяет вызывающий. Снимок совпал с текущим
 * содержимым — база не трогается и версия остаётся прежней.
 */
public final class ModelContentReplaceCommandHandler {

    private final ModelingKernel kernel;
    private final VersionContents contents;

    public ModelContentReplaceCommandHandler(ModelingKernel kernel, VersionContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Номер версии с новым содержимым и сколько объектов в нём непрозрачны. */
    public ReplaceModelContentResponse handle(EditorIdentity actor, ReplaceModelContentCommand command) {
        ModelId modelId = ModelId.of(command.modelId());
        byte[] xml = command.xml();
        return kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.writableModel(modelId, actor);
            Optional<ModelVersion> last = kernel.versions().last(modelId);
            if (last.isPresent() && last.get().contentHash().equals(Snapshots.sha256(xml))
                    && Arrays.equals(contents.currentXml(modelId), xml)) {
                return new ReplaceModelContentResponse(last.get().versionNo(), false, 0);
            }
            VersionContents.Replacement replacement = contents.replace(model, xml, actor, command.comment());
            boolean created = last.map(v -> replacement.version().versionNo() > v.versionNo()).orElse(true);
            return new ReplaceModelContentResponse(replacement.version().versionNo(), created,
                    replacement.opaqueObjects());
        });
    }
}
