package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Рабочее пространство запроса: заданное или по умолчанию. Хозяин пространств — modeling. */
public interface WorkspaceDirectory {

    /** Нет такого пространства — отказ «не найдено». */
    WorkspaceId resolve(Optional<WorkspaceId> requested);
}
