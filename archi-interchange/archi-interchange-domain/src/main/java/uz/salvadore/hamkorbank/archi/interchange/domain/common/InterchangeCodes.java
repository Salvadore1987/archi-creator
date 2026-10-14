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
    /** Коммит в Git не переписывает историю: расхождение не перезаписывается. */
    public static final String GIT_HISTORY = "INV-IXC-009";
    /** Импорт по ссылке применяет только то, что было в отчёте: модель с тех пор не менялась. */
    public static final String GIT_IMPORT = "INV-IXC-012";

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
    public static final String VIEW_NOT_IN_VERSION = "IXC_VIEW_NOT_IN_VERSION";
    public static final String IMAGE_TOO_LARGE = "IXC_IMAGE_TOO_LARGE";
    public static final String INVALID_EXPORT_OPTION = "IXC_INVALID_EXPORT_OPTION";
    /** Выгрузка в OEF не прошла официальную схему — дефект сервера, файл не отдан. */
    public static final String OEF_SCHEMA_VIOLATION = "IXC_OEF_SCHEMA_VIOLATION";
    /** У рабочего пространства нет рабочей привязки к Git. */
    public static final String GIT_NOT_BOUND = "IXC_GIT_NOT_BOUND";
    /** Репозиторий не отвечает или отказывает в доступе; версии ждут в очереди. */
    public static final String GIT_UNREACHABLE = "IXC_GIT_UNREACHABLE";
    /** Ветки, тега или коммита с таким именем в репозитории привязки нет. */
    public static final String GIT_REF_NOT_FOUND = "IXC_GIT_REF_NOT_FOUND";
    /** В коммите нет каталога модели — импортировать нечего. */
    public static final String GIT_MODEL_NOT_IN_REF = "IXC_GIT_MODEL_NOT_IN_REF";

    private InterchangeCodes() {
    }
}
