package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.DisableGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/** Снять привязку: публикация и очистка снимков в пространстве прекращаются, коммиты остаются. */
public final class GitBindingDisableCommandHandler {

    private final InterchangeKernel kernel;
    private final GitBindingRepository bindings;

    public GitBindingDisableCommandHandler(InterchangeKernel kernel, GitBindingRepository bindings) {
        this.kernel = kernel;
        this.bindings = bindings;
    }

    /** Снятая уже привязка не меняется; нет привязки — «не найдено». */
    public void handle(EditorIdentity actor, DisableGitBindingCommand command) {
        kernel.run(InterchangeOperation.CONFIGURE_GIT_BINDING, actor, () -> kernel.unitOfWork().write(() -> {
            WorkspaceId workspace = new WorkspaceId(command.workspaceId());
            GitBinding binding = bindings.find(workspace).orElseThrow(() -> new InterchangeException(
                    InterchangeCodes.NOT_FOUND, Failure.NOT_FOUND,
                    Message.of(InterchangeMessages.GIT_BINDING_MISSING, workspace), Map.of()));
            if (binding.writable()) {
                bindings.save(binding.disabled());
            }
            return null;
        }));
    }
}
