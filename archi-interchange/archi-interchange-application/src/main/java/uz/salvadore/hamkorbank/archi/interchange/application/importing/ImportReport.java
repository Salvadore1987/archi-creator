package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;

/**
 * Отчёт об импорте — и у принятого, и у отклонённого: отказ без объяснения превращает
 * строгий импорт в чёрный ящик (aggregates.yaml#ImportSession).
 *
 * @param replayed повтор по ключу идемпотентности: отдан результат первой попытки (INV-IXC-003)
 * @param rejectedAsCorrupt отказ на разборе — повреждённые данные (FR-50), а не методология
 */
public record ImportReport(ImportSessionId sessionId, ImportStatus status, Optional<java.util.UUID> modelId,
                           long versionNo, List<ImportFinding> findings, boolean replayed,
                           boolean rejectedAsCorrupt) {

    public ImportReport {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(modelId, "modelId");
        findings = List.copyOf(findings);
    }
}
