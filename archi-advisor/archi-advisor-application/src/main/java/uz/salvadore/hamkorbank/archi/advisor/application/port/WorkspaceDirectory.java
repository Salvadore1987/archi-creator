package uz.salvadore.hamkorbank.archi.advisor.application.port;

import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Рабочее пространство запроса: заданное или по умолчанию. Хозяин пространств — modeling. */
public interface WorkspaceDirectory {

    /** {@code requested} = {@code null} — пространство по умолчанию; нет такого пространства — отказ «не найдено». */
    WorkspaceId resolve(@Nullable WorkspaceId requested);
}
