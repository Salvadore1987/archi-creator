package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Что картинка передаёт не так, как модель: объект и аспект, с причиной ключом
 * сообщения — текст собирается на языке запроса.
 *
 * @param objectKind {@code node}, {@code edge} или {@code view}
 * @param aspect     {@code shape}, {@code font}, {@code label}, {@code image}, {@code route}
 */
public record ImageLoss(String archiId, String objectKind, String aspect, Message reason) {

    public ImageLoss {
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(objectKind, "objectKind");
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(reason, "reason");
    }
}
