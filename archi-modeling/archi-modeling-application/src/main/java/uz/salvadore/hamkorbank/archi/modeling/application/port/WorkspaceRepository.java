package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.List;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/** Хранилище рабочих пространств. */
public interface WorkspaceRepository {

    /** Все пространства по времени создания. */
    List<Workspace> findAll();

    /** Самое раннее пространство; на пустой базе — пусто. */
    Optional<Workspace> first();

    /** Пространство по идентификатору. */
    Optional<Workspace> find(WorkspaceId id);

    /** Вставляет новое пространство. */
    void insert(Workspace workspace);
}
