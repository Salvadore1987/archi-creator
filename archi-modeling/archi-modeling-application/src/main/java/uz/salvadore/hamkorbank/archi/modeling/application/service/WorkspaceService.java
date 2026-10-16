package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Рабочие пространства. Первое заводится при старте приложения, если их нет вовсе:
 * миграция строки не сажает — её {@code id} пришлось бы выдумать (журнал этапа 0).
 */
public final class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final UnitOfWork unitOfWork;
    private final TextCatalog texts;
    private final Clock clock;

    public WorkspaceService(WorkspaceRepository workspaces, UnitOfWork unitOfWork, TextCatalog texts, Clock clock) {
        this.workspaces = workspaces;
        this.unitOfWork = unitOfWork;
        this.texts = texts;
        this.clock = clock;
    }

    /** Все рабочие пространства. */
    public List<Workspace> list() {
        return unitOfWork.read(workspaces::findAll);
    }

    /**
     * Пространство команды: заданное клиентом — или первое, если клиент его не назвал.
     * Пространств нет вовсе — отказ «не найдено», а не сбой.
     */
    public WorkspaceId resolve(Optional<WorkspaceId> requested) {
        return requested.orElseGet(() -> unitOfWork.read(workspaces::first)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.ANY_WORKSPACE))).id());
    }

    /**
     * Первое пространство на пустой базе. Имя, не заданное установкой, — текст ресурса
     * на текущем языке; записывается один раз и дальше хранится как данные.
     */
    public Workspace ensureDefault(Optional<String> name) {
        return unitOfWork.write(() -> workspaces.first().orElseGet(() -> {
            String title = name.orElseGet(() -> texts.text(Message.of(ModelingMessages.DEFAULT_WORKSPACE_NAME)));
            Workspace workspace = Workspace.restore(WorkspaceId.next(new UuidV7(clock)), title, clock.instant());
            workspaces.insert(workspace);
            return workspace;
        }));
    }
}
