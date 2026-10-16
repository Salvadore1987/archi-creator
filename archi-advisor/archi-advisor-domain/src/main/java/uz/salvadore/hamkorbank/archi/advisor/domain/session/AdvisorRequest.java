package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;

/**
 * Что спрошено у помощника: функция и её параметры. Сам запрос не хранится —
 * хранится его отпечаток, по которому повтор с тем же ключом отличается
 * от конфликта.
 *
 * @param viewId    область — одно представление (ревью, документация); {@code null} — не задано
 * @param elementId элемент, {@code archiId}: подсказка по нему или анализ влияния; {@code null} — не задан,
 *                  пустой приводится к {@code null}; вид — как у {@link ElementRef}
 * @param prompt    описание фрагмента архитектуры для плана; {@code null} — не задано, пустое
 *                  приводится к {@code null}
 */
public record AdvisorRequest(AdvisorFunction function, ModelId modelId, ViewId viewId, String elementId,
                             String prompt) {

    /** Наибольшая длина описания для плана, в символах. */
    public static final int PROMPT_MAX = 4000;

    public AdvisorRequest {
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(modelId, "modelId");
        if (elementId != null && elementId.isBlank()) {
            elementId = null;
        }
        if (elementId != null && !ElementRef.validArchiId(elementId)) {
            throw new InvalidValueException(AdvisorMessages.ELEMENT_ID_INVALID);
        }
        if (prompt != null) {
            prompt = prompt.strip();
            if (prompt.isEmpty()) {
                prompt = null;
            }
        }
        switch (function) {
            case EXPLAIN -> {
                if (elementId == null) {
                    throw new InvalidValueException(AdvisorMessages.ELEMENT_REQUIRED);
                }
                requireAbsent(viewId == null && prompt == null);
            }
            case PLAN -> {
                if (prompt == null) {
                    throw new InvalidValueException(AdvisorMessages.PROMPT_REQUIRED);
                }
                requireAbsent(viewId == null && elementId == null);
            }
            case DOCUMENT -> {
                if (viewId != null && elementId != null) {
                    throw new InvalidValueException(AdvisorMessages.DOCUMENT_TARGET_AMBIGUOUS);
                }
                requireAbsent(prompt == null);
            }
            case REVIEW -> requireAbsent(elementId == null && prompt == null);
        }
        if (prompt != null && prompt.length() > PROMPT_MAX) {
            throw new InvalidValueException(AdvisorMessages.PROMPT_TOO_LONG, PROMPT_MAX);
        }
    }

    /** Параметр, который функции не нужен, — отказ: он изменил бы отпечаток и попал бы в сессию. */
    private static void requireAbsent(boolean absent) {
        if (!absent) {
            throw new InvalidValueException(AdvisorMessages.PARAMETER_NOT_FOR_FUNCTION);
        }
    }

    /** Ревью всей модели или одного представления; {@code viewId} = {@code null} — вся модель. */
    public static AdvisorRequest review(ModelId modelId, ViewId viewId) {
        return new AdvisorRequest(AdvisorFunction.REVIEW, modelId, viewId, null, null);
    }

    /** Подсказка по элементу с этим {@code archiId}. */
    public static AdvisorRequest explain(ModelId modelId, String elementId) {
        return new AdvisorRequest(AdvisorFunction.EXPLAIN, modelId, null, elementId, null);
    }

    /** План фрагмента архитектуры по описанию. */
    public static AdvisorRequest plan(ModelId modelId, String prompt) {
        return new AdvisorRequest(AdvisorFunction.PLAN, modelId, null, null, prompt);
    }

    /** Документация по модели, представлению или элементу; не заданная область — {@code null}. */
    public static AdvisorRequest document(ModelId modelId, ViewId viewId, String elementId) {
        return new AdvisorRequest(AdvisorFunction.DOCUMENT, modelId, viewId, elementId, null);
    }

    /** SHA-256 параметров: одинаковый у одинаковых запросов, текст описания по нему не восстановить. */
    public String fingerprint() {
        String canonical = String.join("\u0000", function.name(), modelId.toString(),
                viewId == null ? "" : viewId.toString(), Objects.requireNonNullElse(elementId, ""),
                Objects.requireNonNullElse(prompt, ""));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
