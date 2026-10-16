package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.time.Clock;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EnsureDefaultWorkspaceCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.WorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.WorkspaceDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Первое пространство на пустой базе: миграция строки не сажает — её {@code id} пришлось бы
 * выдумать. Имя, не заданное установкой, — текст ресурса на текущем языке; записывается
 * один раз и дальше хранится как данные.
 */
public final class WorkspaceEnsureDefaultCommandHandler {

    private final WorkspaceRepository workspaces;
    private final UnitOfWork unitOfWork;
    private final TextCatalog texts;
    private final Clock clock;

    public WorkspaceEnsureDefaultCommandHandler(WorkspaceRepository workspaces, UnitOfWork unitOfWork,
                                                TextCatalog texts, Clock clock) {
        this.workspaces = workspaces;
        this.unitOfWork = unitOfWork;
        this.texts = texts;
        this.clock = clock;
    }

    /** Первое пространство — заведённое сейчас или уже существующее. */
    public WorkspaceResponse handle(EnsureDefaultWorkspaceCommand command) {
        return WorkspaceDataMapper.response(unitOfWork.write(() -> workspaces.first().orElseGet(() -> {
            String title = command.name() != null ? command.name()
                    : texts.text(Message.of(ModelingMessages.DEFAULT_WORKSPACE_NAME));
            Workspace workspace = Workspace.create(WorkspaceId.next(new UuidV7(clock)), title, clock.instant());
            workspaces.insert(workspace);
            return workspace;
        })));
    }
}
