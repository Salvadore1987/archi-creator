package uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO interchange по контракту OpenAPI. Отсутствующее значение — {@code null},
 * а не {@code Optional}: так его видит JSON.
 */
public final class Dtos {

    private Dtos() {
    }

    /** Находка импорта: строка XML и {@code archi_id}, если они известны. */
    public record FindingDto(String severity, String code, String message, String archiId, Integer xmlLine) {
    }

    /** Итог импорта: сессия, модель и отчёт — у принятого и у отклонённого. */
    public record ImportResult(UUID sessionId, String status, UUID modelId, Long versionNo, boolean replayed,
                               List<FindingDto> findings) {
    }

    public record ImportSettings(boolean strictImport) {
    }
}
