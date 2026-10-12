package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Выключатель и месячный лимит помощника — настройки рабочего пространства, столбцы advisor. */
public interface AdvisorSettingsRepository {

    StoredSettings find(WorkspaceId workspaceId);

    void save(WorkspaceId workspaceId, boolean enabled, long monthlyTokenLimit);

    /**
     * @param monthlyTokenLimit пусто — лимит не задан, действует значение по умолчанию
     */
    record StoredSettings(boolean enabled, Optional<Long> monthlyTokenLimit) {
    }
}
