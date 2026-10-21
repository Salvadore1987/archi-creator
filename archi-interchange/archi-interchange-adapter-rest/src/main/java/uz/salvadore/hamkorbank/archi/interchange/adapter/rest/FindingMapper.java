package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import java.util.List;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.FindingDto;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportFindingResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;

/** Находки импорта — в DTO с текстом на языке запроса: в ответе и в подробностях отказа. */
final class FindingMapper {

    private FindingMapper() {
    }

    static FindingDto finding(ImportFinding f, TextCatalog text) {
        return new FindingDto(f.severity().name(), f.code(), text.text(f.message()),
                f.archiId() == null ? null : f.archiId().value(), f.xmlLine());
    }

    /** Находки из результата сценария — текст уже на языке запроса. */
    static List<FindingDto> findings(List<ImportFindingResponse> findings) {
        return findings.stream().map(f -> new FindingDto(f.severity(), f.code(), f.message(), f.archiId(),
                f.xmlLine())).toList();
    }

    /** Подробность отказа: список находок переводится, остальное отдаётся как есть. */
    static Object detail(Object value, TextCatalog text) {
        if (value instanceof List<?> list && !list.isEmpty()
                && list.stream().allMatch(ImportFinding.class::isInstance)) {
            return list.stream().map(f -> finding((ImportFinding) f, text)).toList();
        }
        return value;
    }
}
