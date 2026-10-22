package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.Arrays;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Заменить содержимое модели снимком и зафиксировать новой версией.
 *
 * @param xml несжатый {@code .archimate}
 */
public record ReplaceModelContentCommand(UUID modelId, byte[] xml, @Nullable String comment) {

    public ReplaceModelContentCommand {
        xml = Arrays.copyOf(xml, xml.length);
    }

    @Override
    public byte[] xml() {
        return Arrays.copyOf(xml, xml.length);
    }
}
