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

    /** Объект запроса — рабочее пространство, привязка к Git или сессия импорта — не найден. */
    public static final String NOT_FOUND = "IXC_NOT_FOUND";
    /** Роль пользователя не допускает запрошенную операцию импорта или выгрузки. */
    public static final String ACCESS_DENIED = "IXC_ACCESS_DENIED";
    /** Запрос не разобран: поле не задано или значение не того вида. */
    public static final String INVALID_REQUEST = "IXC_INVALID_REQUEST";
    /** В рабочее пространство уже идёт импорт: импорты в одно пространство последовательны. */
    public static final String IMPORT_BUSY = "IXC_IMPORT_BUSY";
    /** Файл или состояние модели в коммите повреждены и не собираются — отказ при любом режиме. */
    public static final String CORRUPT_DOCUMENT = "IXC_CORRUPT_DOCUMENT";
    /** Строгий импорт отклонён: в модели есть связи, которые не допускает матрица ArchiMate. */
    public static final String STRICT_IMPORT_REJECTED = "IXC_STRICT_IMPORT_REJECTED";
    /** Формат выгрузки известен, но пока не поддерживается. */
    public static final String FORMAT_NOT_AVAILABLE = "IXC_FORMAT_NOT_AVAILABLE";
    /** Запрошен неизвестный формат выгрузки. */
    public static final String UNKNOWN_FORMAT = "IXC_UNKNOWN_FORMAT";
    /** Находка импорта: тип объекта неизвестен метамодели, объект сохранён как есть. */
    public static final String UNKNOWN_ELEMENT_TYPE = "IXC_UNKNOWN_ELEMENT_TYPE";
    /** Загружаемый файл больше допустимого размера. */
    public static final String FILE_TOO_LARGE = "IXC_FILE_TOO_LARGE";
    /** Представления нет в зафиксированной версии — картинка строится только из сохранённого. */
    public static final String VIEW_NOT_IN_VERSION = "IXC_VIEW_NOT_IN_VERSION";
    /** Папки, которой ограничен каталог, нет в зафиксированной версии. */
    public static final String FOLDER_NOT_IN_VERSION = "IXC_FOLDER_NOT_IN_VERSION";
    /** Картинка представления при заданной плотности больше предела по стороне. */
    public static final String IMAGE_TOO_LARGE = "IXC_IMAGE_TOO_LARGE";
    /** Параметр выгрузки картинки задан недопустимым значением. */
    public static final String INVALID_EXPORT_OPTION = "IXC_INVALID_EXPORT_OPTION";
    /** Выгрузка в OEF не прошла официальную схему — дефект сервера, файл не отдан. */
    public static final String OEF_SCHEMA_VIOLATION = "IXC_OEF_SCHEMA_VIOLATION";
    /** У рабочего пространства нет рабочей привязки к Git. */
    public static final String GIT_NOT_BOUND = "IXC_GIT_NOT_BOUND";
    /** Действие над привязкой к Git, которая снята. */
    public static final String GIT_BINDING_DISABLED = "IXC_GIT_BINDING_DISABLED";
    /** Задание выгрузки получило команду, недопустимую из его состояния. */
    public static final String EXPORT_TRANSITION = "IXC_EXPORT_TRANSITION";
    /** Остаток XML в хранилище повреждён и не читается. */
    public static final String RESIDUE_CORRUPTED = "IXC_RESIDUE_CORRUPTED";
    /** Репозиторий не отвечает или отказывает в доступе; версии ждут в очереди. */
    public static final String GIT_UNREACHABLE = "IXC_GIT_UNREACHABLE";
    /** Ветки, тега или коммита с таким именем в репозитории привязки нет. */
    public static final String GIT_REF_NOT_FOUND = "IXC_GIT_REF_NOT_FOUND";
    /** В коммите нет каталога модели — импортировать нечего. */
    public static final String GIT_MODEL_NOT_IN_REF = "IXC_GIT_MODEL_NOT_IN_REF";
    /** Сливать не с чем: нет опубликованной версии, общего предка или каталога модели в нём. */
    public static final String GIT_NO_MERGE_BASE = "IXC_GIT_NO_MERGE_BASE";
    /** Слияние применяется только с выбором по каждому конфликту. */
    public static final String MERGE_CONFLICTS_UNRESOLVED = "IXC_MERGE_CONFLICTS_UNRESOLVED";

    // Дефекты файла: код находки чтения, по нему клиент показывает место поломки.
    /** Файл не разбирается как XML. */
    public static final String MALFORMED_XML = "IXC_MALFORMED_XML";
    /** Корень — не {@code archimate:model}. */
    public static final String NOT_ARCHIMATE_MODEL = "IXC_NOT_ARCHIMATE_MODEL";
    /** Нет обязательного атрибута: {@code id}, {@code xsi:type}. */
    public static final String MISSING_ATTRIBUTE = "IXC_MISSING_ATTRIBUTE";
    /** Идентификатор не годится для атрибута {@code id}. */
    public static final String INVALID_ID = "IXC_INVALID_ID";
    /** Идентификатор повторяется внутри файла. */
    public static final String DUPLICATE_ID = "IXC_DUPLICATE_ID";
    /** Ссылка на идентификатор, которого в файле нет. */
    public static final String DANGLING_REFERENCE = "IXC_DANGLING_REFERENCE";
    /** У связи или соединения нет одного из концов. */
    public static final String MISSING_END = "IXC_MISSING_END";
    /** {@code targetConnections} перечисляет не те соединения, что ведут к узлу. */
    public static final String TARGET_CONNECTIONS_MISMATCH = "IXC_TARGET_CONNECTIONS_MISMATCH";
    /** Текст там, где ждут только узлы: его некуда положить без потери. */
    public static final String UNEXPECTED_TEXT = "IXC_UNEXPECTED_TEXT";
    /** Узлы вложены глубже предела разбора. */
    public static final String NESTING_TOO_DEEP = "IXC_NESTING_TOO_DEEP";
    /** Узел не на своём месте в дереве: папка в представлении, узел представления в папке. */
    public static final String MISPLACED_NODE = "IXC_MISPLACED_NODE";

    private InterchangeCodes() {
    }
}
