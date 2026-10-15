package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

public interface WorkspaceRepository {

    List<Workspace> findAll();

    /** Самое раннее пространство; на пустой базе — пусто. */
    Optional<Workspace> first();

    Optional<Workspace> find(WorkspaceId id);

    void insert(Workspace workspace);
}
