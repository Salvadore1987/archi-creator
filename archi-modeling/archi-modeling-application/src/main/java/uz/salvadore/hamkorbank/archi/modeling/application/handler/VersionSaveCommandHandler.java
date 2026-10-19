package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.SaveVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.VersionDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Сохранение модели версией. Изменений с последней версии нет — версия не создаётся:
 * журнал версий — не журнал нажатий. Повтор с тем же ключом отдаёт первую версию.
 */
public final class VersionSaveCommandHandler {

    private final ModelingKernel kernel;
    private final VersionCommitter committer;

    public VersionSaveCommandHandler(ModelingKernel kernel, VersionCommitter committer) {
        this.kernel = kernel;
        this.committer = committer;
    }

    /** Новая версия или последняя, если менять нечего. */
    public SaveVersionResponse handle(EditorIdentity actor, SaveVersionCommand command) {
        return kernel.run(Operation.SAVE_MODEL, actor, () -> kernel.unitOfWork().write(() -> {
            ModelId modelId = ModelId.of(command.modelId());
            boolean[] created = {false};
            ModelVersion version = kernel.idempotent("SaveModel", actor, command.idempotencyKey(),
                    IdempotentCommand.fingerprint(modelId, command.comment()),
                    () -> {
                        ArchitectureModel model = kernel.writableModel(modelId, actor);
                        Optional<Long> before = kernel.versions().last(modelId).map(ModelVersion::versionNo);
                        ModelVersion committed = committer.commit(model, actor, command.comment());
                        created[0] = before.map(no -> committed.versionNo() > no).orElse(true);
                        return String.valueOf(committed.versionNo());
                    },
                    no -> kernel.versions().find(modelId, Long.parseLong(no)).orElseThrow());
            return new SaveVersionResponse(VersionDataMapper.version(version), created[0]);
        }));
    }
}
