package uz.salvadore.hamkorbank.archi.advisor.application.port;

import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Выключатель и месячный лимит помощника — настройки рабочего пространства, столбцы advisor. */
public interface AdvisorSettingsRepository {

    /** Настройки пространства как хранятся, без подстановки лимита по умолчанию. */
    StoredSettings find(WorkspaceId workspaceId);

    /** Записать выключатель и месячный лимит пространства. */
    void save(WorkspaceId workspaceId, boolean enabled, long monthlyTokenLimit);

    /**
     * @param monthlyTokenLimit {@code null} — лимит не задан, действует значение по умолчанию
     */
    record StoredSettings(boolean enabled, Long monthlyTokenLimit) {
    }
}
