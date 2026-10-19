package uz.salvadore.hamkorbank.archi.interchange.application.port;

import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/** Строгость импорта рабочего пространства ({@code workspace.strict_import}). */
public interface ImportPolicy {

    /** Включён ли строгий режим импорта пространства. */
    boolean strictImport(WorkspaceId workspaceId);

    /** Включает или выключает строгий режим импорта пространства. */
    void setStrictImport(WorkspaceId workspaceId, boolean strict);
}
