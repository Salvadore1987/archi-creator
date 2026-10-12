package uz.salvadore.hamkorbank.archi.advisor.domain.common;

/**
 * Коды отказов помощника — значение поля {@code code} ответа {@code problem+json}
 * и метки {@code error_code} метрик. Это контракт API, а не текст: клиент
 * ветвится по коду, человеку показывается сообщение по ключу.
 */
public final class AdvisorCodes {

    /** Повтор с тем же ключом не тратит токены заново; тот же ключ с другими параметрами — конфликт. */
    public static final String IDEMPOTENCY = "INV-ADV-003";
    /** Сессия идёт по разрешённым переходам. */
    public static final String SESSION_TRANSITION = "INV-ADV-002";

    public static final String ADVISOR_DISABLED = "ADVISOR_DISABLED";
    public static final String BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String CONTEXT_TOO_LARGE = "CONTEXT_TOO_LARGE";
    public static final String MODEL_DELETED = "MODEL_DELETED";
    public static final String UNAVAILABLE = "ADVISOR_UNAVAILABLE";
    public static final String NOT_FOUND = "ADV_NOT_FOUND";
    public static final String INVALID_INPUT = "ADV_INVALID_INPUT";
    public static final String ACCESS_DENIED = "ADV_ACCESS_DENIED";

    private AdvisorCodes() {
    }
}
