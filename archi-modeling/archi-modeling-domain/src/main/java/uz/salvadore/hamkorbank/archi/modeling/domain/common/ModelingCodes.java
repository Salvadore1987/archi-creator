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

    /** Матрица ArchiMate не допускает связь этого типа между выбранными концами. */
    public static final String RELATION_NOT_PERMITTED = "RELATION_NOT_PERMITTED";
    /** Модель удалена: выгрузка и замена её содержимого недоступны. */
    public static final String MODEL_DELETED = "MODEL_DELETED";
    /** Снимок версии в хранилище повреждён и не распаковывается — дефект сервера. */
    public static final String SNAPSHOT_CORRUPTED = "MDL_SNAPSHOT_CORRUPTED";
    /** Запрошенный объект не найден или не виден автору запроса. */
    public static final String NOT_FOUND = "MDL_NOT_FOUND";
    /** Запрос не разбирается: поле не задано или значение не того формата. */
    public static final String INVALID_INPUT = "MDL_INVALID_INPUT";
    /** Роли или списка доступа автора не хватает для операции. */
    public static final String ACCESS_DENIED = "MDL_ACCESS_DENIED";
    /** Тип объекта не редактируется в текущей фазе метамодели. */
    public static final String TYPE_NOT_EDITABLE = "MDL_TYPE_NOT_EDITABLE";
    /** Объект неподдержанного типа хранится как есть; находка проверки модели, а не отказ. */
    public static final String OPAQUE_OBJECT = "MDL_OPAQUE_OBJECT";
    /** Удаляемая папка не пуста. */
    public static final String FOLDER_NOT_EMPTY = "MDL_FOLDER_NOT_EMPTY";
    /** Снимок версии очищен по сроку хранения, и восстановить его неоткуда. */
    public static final String SNAPSHOT_PURGED = "MDL_SNAPSHOT_PURGED";
    /** Ключ идемпотентности уже использован для запроса с другим содержимым. */
    public static final String IDEMPOTENCY_CONFLICT = "MDL_IDEMPOTENCY_KEY_REUSED";
    /** Запись нарушила ограничение целостности базы данных. */
    public static final String INTEGRITY_CONFLICT = "MDL_INTEGRITY_CONFLICT";
    /** Идентификатор, заданный клиентом для нового объекта, уже занят. */
    public static final String ID_TAKEN = "MDL_ID_TAKEN";

    private ModelingCodes() {
    }
}
