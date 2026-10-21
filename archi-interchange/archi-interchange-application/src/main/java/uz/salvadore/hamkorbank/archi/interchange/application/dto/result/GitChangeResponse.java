package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

import java.util.List;

/**
 * Объект отчёта об изменениях.
 *
 * @param change {@code ADDED}, {@code REMOVED} или {@code CHANGED}
 * @param type   тип ArchiMate без префикса; у папок и модели {@code null}
 * @param fields изменённые поля
 */
public record GitChangeResponse(String kind, String change, String archiId, String type, String name,
                                List<String> fields) {

    public GitChangeResponse {
        fields = List.copyOf(fields);
    }
}
