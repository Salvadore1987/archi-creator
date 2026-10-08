package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Разобранный и проверенный документ принят (spec/domain/interchange/events.yaml#ImportApplied).
 * Отклонённый импорт события не порождает: его результат — в отчёте сессии.
 *
 * @param opaqueObjectCount сколько объектов сохранено непрозрачными (FR-03)
 */
public record ImportApplied(ImportSessionId importSessionId, WorkspaceId workspaceId, ModelId modelId,
                            long versionNo, String sourceName, ContentHash sourceHash, boolean strictMode,
                            Map<Severity, Integer> findingCounts, int opaqueObjectCount, Instant occurredAt) {

    public ImportApplied {
        Objects.requireNonNull(importSessionId, "importSessionId");
        Objects.requireNonNull(modelId, "modelId");
        findingCounts = Map.copyOf(findingCounts);
    }
}
