package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.VersionDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Откат: содержимое версии становится текущим и фиксируется новой версией — история
 * не переписывается, а дописывается. Очищенный снимок восстанавливается из Git между
 * транзакцией чтения и транзакцией записи: сетевой вызов не держит соединение с базой;
 * доступ и блокировка проверяются в обеих.
 */
public final class VersionRestoreCommandHandler {

    private final ModelingKernel kernel;
    private final VersionContents contents;

    public VersionRestoreCommandHandler(ModelingKernel kernel, VersionContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Новая версия с содержимым старой; пустой комментарий — комментарий по умолчанию. */
    public VersionResponse handle(EditorIdentity actor, RestoreVersionCommand command) {
        return kernel.run(Operation.RESTORE_VERSION, actor, () -> {
            ModelId modelId = ModelId.of(command.modelId());
            Located located = kernel.unitOfWork().read(() -> {
                ModelHeader header = kernel.visibleHeader(modelId, actor, AclAccess.WRITE);
                ModelLock.requireWriteAccess(modelId, kernel.locks().find(modelId).orElse(null), actor.subject(),
                        kernel.now());
                return new Located(header, contents.requireVersion(modelId, command.versionNo()));
            });
            byte[] xml = contents.content(located.header(), located.version());
            String comment = command.comment() == null || command.comment().isBlank()
                    ? kernel.texts().text(Message.of(ModelingMessages.COMMENT_ROLLBACK, command.versionNo()))
                    : command.comment();
            return kernel.unitOfWork().write(() -> {
                ArchitectureModel model = kernel.writableModel(modelId, actor);
                return VersionDataMapper.version(contents.replace(model, xml, actor, comment).version());
            });
        });
    }

    /** Версия и заголовок её модели, прочитанные в транзакции, — до обращения к архиву вне её. */
    private record Located(ModelHeader header, ModelVersion version) {
    }
}
