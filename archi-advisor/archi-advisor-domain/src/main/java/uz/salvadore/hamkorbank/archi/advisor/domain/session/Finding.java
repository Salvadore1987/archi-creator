package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.FindingId;

/**
 * Одно замечание ревью. Без ссылки на объект модели находка не создаётся:
 * замечание, к которому нельзя перейти, архитектор не может ни подтвердить,
 * ни опровергнуть.
 *
 * @param code       машинное имя замечания, например {@code ORPHAN_ELEMENT}
 * @param suggestion как исправить — текст для архитектора, а не команда; {@code null} — совета нет,
 *                   пустой приводится к {@code null}
 */
public record Finding(FindingId id, Severity severity, String code, String message, TargetKind targetKind,
                      ElementRef target, @Nullable String suggestion) {

    /** Наибольшая длина текста замечания и совета, в символах. */
    public static final int TEXT_MAX = 2000;
    /** Наибольшая длина кода замечания. */
    public static final int CODE_MAX = 64;

    public Finding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(targetKind, "targetKind");
        if (target == null) {
            throw new InvalidValueException(AdvisorMessages.FINDING_WITHOUT_TARGET);
        }
        if (code == null || code.isBlank() || code.length() > CODE_MAX) {
            throw new InvalidValueException(AdvisorMessages.FINDING_CODE_INVALID, CODE_MAX);
        }
        if (message == null || message.isBlank() || message.length() > TEXT_MAX) {
            throw new InvalidValueException(AdvisorMessages.FINDING_TEXT_INVALID, TEXT_MAX);
        }
        if (suggestion != null && suggestion.isBlank()) {
            suggestion = null;
        }
        if (suggestion != null && suggestion.length() > TEXT_MAX) {
            throw new InvalidValueException(AdvisorMessages.FINDING_TEXT_INVALID, TEXT_MAX);
        }
    }
}
