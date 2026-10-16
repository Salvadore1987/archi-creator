package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;

/**
 * Отчёт об импорте — и у принятого, и у отклонённого: отказ без объяснения превращает
 * строгий импорт в чёрный ящик.
 *
 * @param modelId  созданная модель; {@code null} — импорт ничего не применил
 * @param replayed повтор по ключу идемпотентности: отдан результат первой попытки
 * @param rejectedAsCorrupt отказ на разборе — повреждённые данные, а не методология
 */
public record ImportReport(ImportSessionId sessionId, ImportStatus status, java.util.UUID modelId,
                           long versionNo, List<ImportFinding> findings, boolean replayed,
                           boolean rejectedAsCorrupt) {

    public ImportReport {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(status, "status");
        findings = List.copyOf(findings);
    }
}
