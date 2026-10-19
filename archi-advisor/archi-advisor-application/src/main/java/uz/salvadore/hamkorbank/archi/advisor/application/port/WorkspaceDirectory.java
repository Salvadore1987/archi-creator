package uz.salvadore.hamkorbank.archi.advisor.application.port;

import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Рабочее пространство запроса: заданное или по умолчанию. Хозяин пространств — modeling. */
public interface WorkspaceDirectory {

    /** {@code requested} = {@code null} — пространство по умолчанию; нет такого пространства — отказ «не найдено». */
    WorkspaceId resolve(WorkspaceId requested);
}
