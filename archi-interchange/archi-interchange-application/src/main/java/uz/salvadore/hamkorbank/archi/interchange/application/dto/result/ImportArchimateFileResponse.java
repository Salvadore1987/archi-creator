package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Итог импорта — и у принятого файла, и у отклонённого: отказ без объяснения превращает строгий
 * импорт в чёрный ящик.
 *
 * @param status        состояние сессии импорта: {@code APPLIED} или {@code REJECTED}
 * @param refusalCode   код отказа; {@code null} — файл принят
 * @param refusalText   объяснение отказа на языке запроса; {@code null} — файл принят
 * @param modelId       созданная модель; {@code null} — импорт ничего не применил
 * @param versionNo     первая версия созданной модели; {@code null} — импорт ничего не применил
 * @param replayed      повтор по ключу идемпотентности: отдан итог первой попытки
 */
public record ImportArchimateFileResponse(UUID sessionId, String status, Outcome outcome, String refusalCode,
                                          String refusalText, UUID modelId, Long versionNo, boolean replayed,
                                          List<ImportFindingResponse> findings) {

    public ImportArchimateFileResponse {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(outcome, "outcome");
        findings = List.copyOf(findings);
    }

    /** Чем кончилась попытка. */
    public enum Outcome {
        /** Модель создана этой попыткой. */
        APPLIED,
        /** Повтор принятой попытки: отдана модель первой. */
        REPLAYED,
        /** Файл повреждён или модель из него не собирается — отказ в любом режиме. */
        REJECTED_CORRUPT,
        /** Строгий режим отклонил файл с нарушениями матрицы связей. */
        REJECTED_STRICT
    }
}
