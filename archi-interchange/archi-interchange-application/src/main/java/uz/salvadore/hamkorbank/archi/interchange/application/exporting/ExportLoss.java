package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossObjectKind;

/**
 * Что выгрузка передаёт не так, как модель, или не передаёт вовсе: объект, аспект и причина
 * ключом сообщения — текст собирается на языке запроса. Одна запись для картинки и для OEF.
 *
 * @param aspect что именно: {@code shape}, {@code font}, {@code label}, {@code style}, {@code route},
 *               {@code type}, {@code attribute:<имя>}, {@code xml:<тег>}, {@code property:<ключ>}, …
 */
public record ExportLoss(String archiId, LossObjectKind objectKind, String aspect, Message reason) {

    public ExportLoss {
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(objectKind, "objectKind");
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(reason, "reason");
    }
}
