package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossObjectKind;

/**
 * Что картинка передаёт не так, как модель: объект и аспект, с причиной ключом
 * сообщения — текст собирается на языке запроса.
 *
 * @param objectKind {@link LossObjectKind#NODE}, {@link LossObjectKind#EDGE} или {@link LossObjectKind#VIEW}
 * @param aspect     {@code shape}, {@code font}, {@code label}, {@code image}, {@code route}
 */
public record ImageLoss(String archiId, LossObjectKind objectKind, String aspect, Message reason) {

    public ImageLoss {
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(objectKind, "objectKind");
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(reason, "reason");
    }
}
