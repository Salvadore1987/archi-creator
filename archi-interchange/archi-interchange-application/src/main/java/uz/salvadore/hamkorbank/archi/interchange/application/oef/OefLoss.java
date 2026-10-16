package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossObjectKind;

/**
 * Что не перенеслось в OEF: объект, аспект и причина ключом сообщения — текст
 * собирается на языке запроса.
 *
 * @param aspect     что именно: {@code type}, {@code attribute:<имя>}, {@code xml:<тег>},
 *                   {@code property:<ключ>}, {@code style:<имя>}, {@code identifier}, …
 */
public record OefLoss(String archiId, LossObjectKind objectKind, String aspect, Message reason) {

    public OefLoss {
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(objectKind, "objectKind");
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(reason, "reason");
    }
}
