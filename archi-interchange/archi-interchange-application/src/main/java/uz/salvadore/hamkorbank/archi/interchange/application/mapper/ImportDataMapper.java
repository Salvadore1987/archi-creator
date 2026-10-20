package uz.salvadore.hamkorbank.archi.interchange.application.mapper;

import java.util.List;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportFindingResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;

/** Находки импорта — в результат сценария, с текстом на языке запроса. */
public final class ImportDataMapper {

    private ImportDataMapper() {
    }

    /** Находка как результат: уровень и код именами, сообщение — текстом. */
    public static ImportFindingResponse finding(ImportFinding finding, TextCatalog texts) {
        return new ImportFindingResponse(finding.severity().name(), finding.code(), texts.text(finding.message()),
                finding.archiId() == null ? null : finding.archiId().value(), finding.xmlLine());
    }

    /** Находки в порядке отчёта. */
    public static List<ImportFindingResponse> findings(List<ImportFinding> findings, TextCatalog texts) {
        return findings.stream().map(f -> finding(f, texts)).toList();
    }
}
