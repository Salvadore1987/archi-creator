package uz.salvadore.hamkorbank.archi.modeling.domain.validation;

import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;

/**
 * Находка валидатора метамодели — тот же формат, что у находок помощника. Текст
 * не хранится: сообщение и подсказка — ключи, их переводит адаптер на язык запроса.
 *
 * @param targetKind {@code ELEMENT}, {@code RELATIONSHIP}
 * @param targetId   {@code archi_id} объекта: по нему находка сверяется с файлом и деревом
 */
public record ValidationFinding(Severity severity, String code, Message message, String targetKind, String targetId,
                                Optional<Message> suggestion) {

    public ValidationFinding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(suggestion, "suggestion");
    }
}
