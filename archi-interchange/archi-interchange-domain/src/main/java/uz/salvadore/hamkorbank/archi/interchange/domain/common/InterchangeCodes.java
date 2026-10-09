package uz.salvadore.hamkorbank.archi.interchange.domain.common;

/**
 * Коды отказов interchange — значение поля {@code code} ответа {@code problem+json}
 * и метки {@code error_code} метрик. Это контракт API, а не текст.
 */
public final class InterchangeCodes {

    /** Узел документа полон: идентификатор, тип, порядок; неизвестное — дословно. */
    public static final String DOCUMENT_NODE = "INV-IXC-001";
    /** Сессия импорта идёт по разрешённым переходам. */
    public static final String IMPORT_TRANSITION = "INV-IXC-002";
    /** Повторная подача того же файла не создаёт вторую модель. */
    public static final String IMPORT_IDEMPOTENCY = "INV-IXC-003";
    /** Выгрузка в формат с потерями обязана вернуть отчёт о потерях. */
    public static final String LOSS_REPORT = "INV-IXC-006";
    /** Выгружается зафиксированная версия, а не текущее состояние. */
    public static final String EXPORT_VERSION = "INV-IXC-008";

    public static final String NOT_FOUND = "IXC_NOT_FOUND";
    public static final String ACCESS_DENIED = "IXC_ACCESS_DENIED";
    public static final String INVALID_REQUEST = "IXC_INVALID_REQUEST";
    public static final String IDEMPOTENCY_CONFLICT = "IXC_IDEMPOTENCY_CONFLICT";
    public static final String CORRUPT_DOCUMENT = "IXC_CORRUPT_DOCUMENT";
    public static final String STRICT_IMPORT_REJECTED = "IXC_STRICT_IMPORT_REJECTED";
    public static final String FORMAT_NOT_AVAILABLE = "IXC_FORMAT_NOT_AVAILABLE";
    public static final String UNKNOWN_FORMAT = "IXC_UNKNOWN_FORMAT";
    public static final String UNKNOWN_ELEMENT_TYPE = "IXC_UNKNOWN_ELEMENT_TYPE";
    public static final String FILE_TOO_LARGE = "IXC_FILE_TOO_LARGE";

    private InterchangeCodes() {
    }
}
