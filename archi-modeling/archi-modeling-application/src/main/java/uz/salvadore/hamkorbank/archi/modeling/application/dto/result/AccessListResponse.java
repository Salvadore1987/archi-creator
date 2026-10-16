package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.List;
import java.util.UUID;

/** Список доступа модели; пустой — модель открыта всем по ролям. */
public record AccessListResponse(UUID modelId, List<Entry> entries) {

    public AccessListResponse {
        entries = List.copyOf(entries);
    }

    /**
     * Строка списка.
     *
     * @param principalType {@code USER} или {@code GROUP}
     * @param access        {@code READ} или {@code WRITE}
     */
    public record Entry(String principalType, String principal, String access) {
    }
}
