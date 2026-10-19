package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.LabelVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.VersionDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/** Метка релиза. Содержимого модели не меняет, поэтому блокировки не требует. */
public final class VersionLabelCommandHandler {

    private final ModelingKernel kernel;
    private final VersionContents contents;

    public VersionLabelCommandHandler(ModelingKernel kernel, VersionContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Версия с новой меткой; пустая метка снимает пометку. */
    public VersionResponse handle(EditorIdentity actor, LabelVersionCommand command) {
        return kernel.run(Operation.LABEL_VERSION, actor, () -> kernel.unitOfWork().write(() -> {
            ModelId modelId = ModelId.of(command.modelId());
            kernel.visibleHeader(modelId, actor, AclAccess.WRITE);
            ModelVersion labelled = contents.requireVersion(modelId, command.versionNo()).labelled(command.label());
            kernel.versions().save(labelled);
            return VersionDataMapper.version(labelled);
        }));
    }
}
