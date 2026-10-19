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

    /** Помощник выключен администратором пространства. */
    public static final String ADVISOR_DISABLED = "ADVISOR_DISABLED";
    /** Месячный бюджет токенов исчерпан; в деталях — дата сброса. */
    public static final String BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED";
    /** Обращения слишком часты; в деталях — сколько секунд ждать. */
    public static final String RATE_LIMITED = "RATE_LIMITED";
    /** Контекст модели не помещается в запрос даже усечённым. */
    public static final String CONTEXT_TOO_LARGE = "CONTEXT_TOO_LARGE";
    /** Модель удалена, обращаться по ней нельзя. */
    public static final String MODEL_DELETED = "MODEL_DELETED";
    /** Внешний API помощника не ответил или оборвал поток. */
    public static final String UNAVAILABLE = "ADVISOR_UNAVAILABLE";
    /** Модель, представление, элемент или пространство не найдены или скрыты списком доступа. */
    public static final String NOT_FOUND = "ADV_NOT_FOUND";
    /** Запрос не прошёл проверку: не задано поле или значение недопустимо. */
    public static final String INVALID_INPUT = "ADV_INVALID_INPUT";
    /** Роль автора не даёт права на операцию. */
    public static final String ACCESS_DENIED = "ADV_ACCESS_DENIED";

    private AdvisorCodes() {
    }
}
