package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Что не перенеслось в OEF: объект, аспект и причина ключом сообщения — текст
 * собирается на языке запроса.
 *
 * @param objectKind {@code model}, {@code folder}, {@code element}, {@code relationship},
 *                   {@code view}, {@code node} или {@code edge}
 * @param aspect     что именно: {@code type}, {@code attribute:<имя>}, {@code xml:<тег>},
 *                   {@code property:<ключ>}, {@code style:<имя>}, {@code identifier}, …
 */
public record OefLoss(String archiId, String objectKind, String aspect, Message reason) {

    public OefLoss {
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(objectKind, "objectKind");
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(reason, "reason");
    }
}
