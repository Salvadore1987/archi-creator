package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

public interface WorkspaceRepository {

    List<Workspace> findAll();

    Optional<Workspace> find(WorkspaceId id);

    void insert(Workspace workspace);
}
