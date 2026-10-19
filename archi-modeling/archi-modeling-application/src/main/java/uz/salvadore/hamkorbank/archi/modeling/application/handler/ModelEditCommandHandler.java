package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Переименование и описание модели; нужна блокировка автора. */
public final class ModelEditCommandHandler {

    private final ModelingKernel kernel;

    public ModelEditCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Заданные поля меняются, незаданные остаются. */
    public ModelSummaryResponse handle(EditorIdentity actor, EditModelCommand command) {
        return kernel.run(Operation.RENAME_MODEL, actor, () -> kernel.unitOfWork().write(() -> {
            ArchitectureModel model = kernel.writableModel(ModelId.of(command.modelId()), actor);
            if (command.name() != null) {
                model.rename(command.name(), kernel.now());
            }
            if (command.documentation() != null) {
                model.changeDocumentation(command.documentation(), kernel.now());
            }
            kernel.save(model);
            return ModelDataMapper.summary(model.header());
        }));
    }
}
