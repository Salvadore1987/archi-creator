package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.List;
import java.util.UUID;

/** Заменить список доступа модели целиком: команда описывает итог, а не правку. */
public record ReplaceAccessListCommand(UUID modelId, List<Entry> entries) {

    public ReplaceAccessListCommand {
        entries = List.copyOf(entries);
    }

    /**
     * Строка списка, как её прислал клиент.
     *
     * @param principalType {@code USER} или {@code GROUP}
     * @param access        {@code READ} или {@code WRITE}
     */
    public record Entry(String principalType, String principal, String access) {
    }
}
