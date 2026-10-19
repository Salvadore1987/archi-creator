package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.Arrays;
import java.util.UUID;

/**
 * Заменить содержимое модели снимком и зафиксировать новой версией.
 *
 * @param xml несжатый {@code .archimate}
 */
public record ReplaceModelContentCommand(UUID modelId, byte[] xml, String comment) {

    public ReplaceModelContentCommand {
        xml = Arrays.copyOf(xml, xml.length);
    }

    @Override
    public byte[] xml() {
        return Arrays.copyOf(xml, xml.length);
    }
}
