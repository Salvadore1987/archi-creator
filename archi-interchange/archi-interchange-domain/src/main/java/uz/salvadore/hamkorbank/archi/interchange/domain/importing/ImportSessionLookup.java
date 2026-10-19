package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/** Поиск сессии по ключу идемпотентности. Реализует хранилище сессий. */
public interface ImportSessionLookup {

    /** Сессия пространства с данным ключом идемпотентности. */
    Optional<ImportSession> findByIdempotencyKey(WorkspaceId workspaceId, String idempotencyKey);
}
