package uz.salvadore.hamkorbank.archi.modeling.domain.validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Находка валидатора метамодели — тот же формат, что у находок помощника (§5.1).
 *
 * @param targetKind {@code ELEMENT}, {@code RELATIONSHIP}
 * @param targetId   {@code archi_id} объекта: по нему находка сверяется с файлом и деревом
 */
public record ValidationFinding(Severity severity, String code, String message, String targetKind, String targetId,
                                Optional<String> suggestion) {

    public ValidationFinding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(suggestion, "suggestion");
    }
}
