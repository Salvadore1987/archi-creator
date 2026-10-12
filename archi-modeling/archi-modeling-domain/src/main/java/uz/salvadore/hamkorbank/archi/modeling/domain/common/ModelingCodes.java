package uz.salvadore.hamkorbank.archi.modeling.domain.common;

/**
 * Коды отказов modeling — значение поля {@code code} ответа {@code problem+json}
 * и метки {@code error_code} метрик. Это контракт API, а не текст: клиент
 * ветвится по коду, человеку показывается сообщение по ключу.
 */
public final class ModelingCodes {

    /** {@code archi_id} уникален в модели и имеет формат Archi. */
    public static final String ARCHI_ID_UNIQUE = "INV-MDL-001";
    /** Жизненный цикл модели идёт по разрешённым переходам. */
    public static final String LIFECYCLE = "INV-MDL-002";
    /** Повтор команды с тем же ключом идемпотентности не меняет состояние. */
    public static final String IDEMPOTENCY = "INV-MDL-003";
    /** Связь соединяет концепты своей модели; концепт со связями не удаляется. */
    public static final String RELATIONSHIP_ENDS = "INV-MDL-004";
    /** Порядок узлов разреженный и уникальный в пределах родителя. */
    public static final String SORT_ORDER = "INV-MDL-005";
    /** Запись требует действующей блокировки автора команды. */
    public static final String LOCK_REQUIRED = "INV-MDL-006";
    /** Новая связь допустима по матрице ArchiMate 3.2. */
    public static final String RELATION_MATRIX = "INV-MDL-007";
    /** Узел представления ссылается на объект своей модели. */
    public static final String VIEW_REFERENCE = "INV-MDL-008";
    /** Дерево папок: девять корней, без циклов, раскладка по видам. */
    public static final String FOLDER_TREE = "INV-MDL-009";
    /** Версия неизменяема, снимок чистится только при настроенном Git. */
    public static final String VERSION = "INV-MDL-010";
    /** Список доступа сужает роли, но не расширяет. */
    public static final String ACCESS_LIST = "INV-MDL-011";

    public static final String RELATION_NOT_PERMITTED = "RELATION_NOT_PERMITTED";
    public static final String MODEL_DELETED = "MODEL_DELETED";
    public static final String NOT_FOUND = "MDL_NOT_FOUND";
    public static final String INVALID_INPUT = "MDL_INVALID_INPUT";
    public static final String ACCESS_DENIED = "MDL_ACCESS_DENIED";
    public static final String TYPE_NOT_EDITABLE = "MDL_TYPE_NOT_EDITABLE";
    public static final String OPAQUE_OBJECT = "MDL_OPAQUE_OBJECT";
    public static final String FOLDER_NOT_EMPTY = "MDL_FOLDER_NOT_EMPTY";
    public static final String SNAPSHOT_PURGED = "MDL_SNAPSHOT_PURGED";
    public static final String IDEMPOTENCY_CONFLICT = "MDL_IDEMPOTENCY_KEY_REUSED";
    public static final String INTEGRITY_CONFLICT = "MDL_INTEGRITY_CONFLICT";
    /** Идентификатор, заданный клиентом для нового объекта, уже занят. */
    public static final String ID_TAKEN = "MDL_ID_TAKEN";

    private ModelingCodes() {
    }
}
