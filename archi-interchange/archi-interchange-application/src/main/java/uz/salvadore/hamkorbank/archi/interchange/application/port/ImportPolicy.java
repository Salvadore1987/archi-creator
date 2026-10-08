package uz.salvadore.hamkorbank.archi.interchange.application.port;

import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/** Строгость импорта рабочего пространства ({@code workspace.strict_import}). */
public interface ImportPolicy {

    boolean strictImport(WorkspaceId workspaceId);

    void setStrictImport(WorkspaceId workspaceId, boolean strict);
}
