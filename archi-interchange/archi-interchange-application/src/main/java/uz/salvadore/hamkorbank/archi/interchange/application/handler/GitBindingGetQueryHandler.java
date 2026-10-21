package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetGitBindingQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitBindingResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.GitDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/** Привязка пространства к репозиторию — тем, кто её настраивает; нет привязки — «не найдено». */
public final class GitBindingGetQueryHandler {

    private final InterchangeKernel kernel;
    private final GitBindingRepository bindings;

    public GitBindingGetQueryHandler(InterchangeKernel kernel, GitBindingRepository bindings) {
        this.kernel = kernel;
        this.bindings = bindings;
    }

    /** Текущая привязка, без токена. */
    public GitBindingResponse handle(EditorIdentity actor, GetGitBindingQuery query) {
        return kernel.run(InterchangeOperation.CONFIGURE_GIT_BINDING, actor, () -> kernel.unitOfWork().read(() -> {
            WorkspaceId workspace = new WorkspaceId(query.workspaceId());
            return GitDataMapper.binding(bindings.find(workspace).orElseThrow(() -> new InterchangeException(
                    InterchangeCodes.NOT_FOUND, Failure.NOT_FOUND,
                    Message.of(InterchangeMessages.GIT_BINDING_MISSING, workspace), Map.of())));
        }));
    }
}
