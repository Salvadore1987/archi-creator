package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;

/**
 * Что спрошено у помощника: функция и её параметры. Сам запрос не хранится —
 * хранится его отпечаток, по которому повтор с тем же ключом отличается
 * от конфликта.
 *
 * @param viewId    область — одно представление (ревью, документация)
 * @param elementId элемент, {@code archiId}: подсказка по нему или анализ влияния
 * @param prompt    описание фрагмента архитектуры для плана
 */
public record AdvisorRequest(AdvisorFunction function, ModelId modelId, Optional<ViewId> viewId,
                             Optional<String> elementId, Optional<String> prompt) {

    public static final int PROMPT_MAX = 4000;

    public AdvisorRequest {
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(modelId, "modelId");
        viewId = Objects.requireNonNullElse(viewId, Optional.empty());
        elementId = Objects.requireNonNullElse(elementId, Optional.<String>empty()).filter(e -> !e.isBlank());
        prompt = Objects.requireNonNullElse(prompt, Optional.<String>empty()).map(String::strip)
                .filter(p -> !p.isEmpty());
        switch (function) {
            case EXPLAIN -> {
                if (elementId.isEmpty()) {
                    throw new InvalidValueException(AdvisorMessages.ELEMENT_REQUIRED);
                }
            }
            case PLAN -> {
                if (prompt.isEmpty()) {
                    throw new InvalidValueException(AdvisorMessages.PROMPT_REQUIRED);
                }
            }
            case DOCUMENT -> {
                if (viewId.isPresent() && elementId.isPresent()) {
                    throw new InvalidValueException(AdvisorMessages.DOCUMENT_TARGET_AMBIGUOUS);
                }
            }
            case REVIEW -> {
            }
        }
        if (prompt.map(String::length).orElse(0) > PROMPT_MAX) {
            throw new InvalidValueException(AdvisorMessages.PROMPT_TOO_LONG, PROMPT_MAX);
        }
    }

    public static AdvisorRequest review(ModelId modelId, Optional<ViewId> viewId) {
        return new AdvisorRequest(AdvisorFunction.REVIEW, modelId, viewId, Optional.empty(), Optional.empty());
    }

    public static AdvisorRequest explain(ModelId modelId, String elementId) {
        return new AdvisorRequest(AdvisorFunction.EXPLAIN, modelId, Optional.empty(), Optional.ofNullable(elementId),
                Optional.empty());
    }

    public static AdvisorRequest plan(ModelId modelId, String prompt) {
        return new AdvisorRequest(AdvisorFunction.PLAN, modelId, Optional.empty(), Optional.empty(),
                Optional.ofNullable(prompt));
    }

    public static AdvisorRequest document(ModelId modelId, Optional<ViewId> viewId, Optional<String> elementId) {
        return new AdvisorRequest(AdvisorFunction.DOCUMENT, modelId, viewId, elementId, Optional.empty());
    }

    /** SHA-256 параметров: одинаковый у одинаковых запросов, текст описания по нему не восстановить. */
    public String fingerprint() {
        String canonical = String.join("\u0000", function.name(), modelId.toString(),
                viewId.map(Object::toString).orElse(""), elementId.orElse(""), prompt.orElse(""));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
