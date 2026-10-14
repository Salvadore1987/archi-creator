package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import java.util.List;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.FindingDto;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;

/** Находки импорта — в DTO с текстом на языке запроса: в ответе и в подробностях отказа. */
final class FindingMapper {

    private FindingMapper() {
    }

    static FindingDto finding(ImportFinding f, TextCatalog text) {
        return new FindingDto(f.severity().name(), f.code(), text.text(f.message()),
                f.archiId().map(a -> a.value()).orElse(null),
                f.xmlLine().orElse(null));
    }

    static List<FindingDto> findings(List<ImportFinding> findings, TextCatalog text) {
        return findings.stream().map(f -> finding(f, text)).toList();
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
