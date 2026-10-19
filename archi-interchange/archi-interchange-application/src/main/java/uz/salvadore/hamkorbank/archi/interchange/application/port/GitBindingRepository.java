package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/** Привязки рабочих пространств к репозиториям. */
public interface GitBindingRepository {

    /** Привязка пространства, если она задана. */
    Optional<GitBinding> find(WorkspaceId workspaceId);

    /** Заменяет привязку пространства целиком, вместе с авторами. */
    void save(GitBinding binding);

    /** Есть ли рабочее пространство с таким идентификатором. */
    boolean workspaceExists(WorkspaceId workspaceId);
}
