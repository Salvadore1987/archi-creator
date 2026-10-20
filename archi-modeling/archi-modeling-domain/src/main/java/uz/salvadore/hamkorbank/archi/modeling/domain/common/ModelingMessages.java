package uz.salvadore.hamkorbank.archi.modeling.domain.common;

/**
 * Ключи сообщений modeling. Текст — в {@code i18n/messages*.properties}, по языку запроса;
 * в коде — только ключ и аргументы.
 */
public final class ModelingMessages {

    /** Операция недоступна ролям автора запроса. */
    public static final String OPERATION_DENIED = "modeling.access.operation-denied";
    /** Название операции в отказе роли: открытие модели. */
    public static final String OPERATION_OPEN_MODEL = "modeling.operation.open-model";
    /** Название операции в отказе роли: открытие представления. */
    public static final String OPERATION_OPEN_VIEW = "modeling.operation.open-view";
    /** Название операции в отказе роли: проверка модели. */
    public static final String OPERATION_VALIDATE_MODEL = "modeling.operation.validate-model";
    /** Название операции в отказе роли: просмотр версий. */
    public static final String OPERATION_LIST_VERSIONS = "modeling.operation.list-versions";
    /** Название операции в отказе роли: создание модели. */
    public static final String OPERATION_CREATE_MODEL = "modeling.operation.create-model";
    /** Название операции в отказе роли: переименование модели. */
    public static final String OPERATION_RENAME_MODEL = "modeling.operation.rename-model";
    /** Название операции в отказе роли: захват блокировки. */
    public static final String OPERATION_ACQUIRE_LOCK = "modeling.operation.acquire-lock";
    /** Название операции в отказе роли: снятие блокировки. */
    public static final String OPERATION_RELEASE_LOCK = "modeling.operation.release-lock";
    /** Название операции в отказе роли: создание элемента. */
    public static final String OPERATION_CREATE_ELEMENT = "modeling.operation.create-element";
    /** Название операции в отказе роли: правка элемента. */
    public static final String OPERATION_UPDATE_ELEMENT = "modeling.operation.update-element";
    /** Название операции в отказе роли: удаление элемента. */
    public static final String OPERATION_DELETE_ELEMENT = "modeling.operation.delete-element";
    /** Название операции в отказе роли: создание связи. */
    public static final String OPERATION_CREATE_RELATIONSHIP = "modeling.operation.create-relationship";
    /** Название операции в отказе роли: правка связи. */
    public static final String OPERATION_UPDATE_RELATIONSHIP = "modeling.operation.update-relationship";
    /** Название операции в отказе роли: удаление связи. */
    public static final String OPERATION_DELETE_RELATIONSHIP = "modeling.operation.delete-relationship";
    /** Название операции в отказе роли: создание представления. */
    public static final String OPERATION_CREATE_VIEW = "modeling.operation.create-view";
    /** Название операции в отказе роли: правка представления. */
    public static final String OPERATION_SAVE_VIEW_LAYOUT = "modeling.operation.save-view-layout";
    /** Название операции в отказе роли: авторазметка представления. */
    public static final String OPERATION_AUTO_LAYOUT_VIEW = "modeling.operation.auto-layout-view";
    /** Название операции в отказе роли: правка дерева модели. */
    public static final String OPERATION_REORGANIZE_TREE = "modeling.operation.reorganize-tree";
    /** Название операции в отказе роли: сохранение модели. */
    public static final String OPERATION_SAVE_MODEL = "modeling.operation.save-model";
    /** Название операции в отказе роли: откат к версии. */
    public static final String OPERATION_RESTORE_VERSION = "modeling.operation.restore-version";
    /** Название операции в отказе роли: пометка версии. */
    public static final String OPERATION_LABEL_VERSION = "modeling.operation.label-version";
    /** Название операции в отказе роли: удаление модели. */
    public static final String OPERATION_DELETE_MODEL = "modeling.operation.delete-model";
    /** Название операции в отказе роли: восстановление модели. */
    public static final String OPERATION_RESTORE_MODEL = "modeling.operation.restore-model";
    /** Название операции в отказе роли: уничтожение модели. */
    public static final String OPERATION_PURGE_MODEL = "modeling.operation.purge-model";
    /** Название операции в отказе роли: управление доступом к модели. */
    public static final String OPERATION_MANAGE_MODEL_ACCESS = "modeling.operation.manage-model-access";
    /** Запись списка доступа без субъекта или со слишком длинным субъектом. */
    public static final String ACL_ENTRY_INVALID = "modeling.acl.entry-invalid";
    /** Список доступа модели пытается изменить не администратор и не её автор. */
    public static final String ACL_MANAGERS_ONLY = "modeling.acl.managers-only";
    /** Список доступа модели разрешает автору только чтение. */
    public static final String ACL_READ_ONLY = "modeling.acl.read-only";
    /** Идентификатор Archi недопустимого формата. */
    public static final String ARCHI_ID_INVALID = "modeling.archi-id.invalid";
    /** Идентификатор Archi встречается в модели дважды. */
    public static final String ARCHI_ID_REPEATED = "modeling.archi-id.repeated";
    /** Идентификатор, заданный клиентом, недопустим для Archi. */
    public static final String ARCHI_ID_REQUESTED_INVALID = "modeling.archi-id.requested-invalid";
    /** Идентификатор Archi уже занят другим объектом модели. */
    public static final String ARCHI_ID_TAKEN = "modeling.archi-id.taken";
    /** Объект изменили параллельно: нужно перечитать его и повторить команду. */
    public static final String CONCURRENT_MODIFICATION = "modeling.concurrent-modification";
    /** У команды не указан автор. */
    public static final String EDITOR_WITHOUT_SUBJECT = "modeling.editor.without-subject";
    /** Подпись поля «комментарий» версии — аргумент сообщений о длине. */
    public static final String FIELD_COMMENT = "modeling.field.comment";
    /** Подпись поля «метка» версии — аргумент сообщений о длине. */
    public static final String FIELD_LABEL = "modeling.field.label";
    /** Подпись поля «имя» — аргумент отказа о недопустимом тексте. */
    public static final String FIELD_NAME = "modeling.field.name";
    /** Подпись поля «документация» — аргумент отказа о недопустимом тексте. */
    public static final String FIELD_DOCUMENTATION = "modeling.field.documentation";
    /** Подпись поля «точка зрения» — аргумент отказа о недопустимом тексте. */
    public static final String FIELD_VIEWPOINT = "modeling.field.viewpoint";
    /** Подпись поля «ключ свойства» — аргумент отказа о недопустимом тексте. */
    public static final String FIELD_PROPERTY_KEY = "modeling.field.property-key";
    /** Подпись поля «значение свойства» — аргумент отказа о недопустимом тексте. */
    public static final String FIELD_PROPERTY_VALUE = "modeling.field.property-value";
    /** Подпись поля «подпись узла» — аргумент отказа о недопустимом тексте. */
    public static final String FIELD_NODE_LABEL = "modeling.field.node-label";
    /** Подпись поля «текст заметки» — аргумент отказа о недопустимом тексте. */
    public static final String FIELD_NODE_CONTENT = "modeling.field.node-content";
    /** Подпись поля «шрифт» — аргумент отказа о недопустимом тексте. */
    public static final String FIELD_FONT = "modeling.field.font";
    /** В тексте поля есть символы, которых не может быть в файле Archi: управляющие или одиночные суррогаты. */
    public static final String TEXT_NOT_XML = "modeling.text.not-xml";
    /** Дерево папок модели замкнулось в цикл. */
    public static final String FOLDER_CYCLE = "modeling.folder.cycle";
    /** Папку переносят внутрь неё самой. */
    public static final String FOLDER_INTO_ITSELF = "modeling.folder.into-itself";
    /** Удаляемая папка не пуста. */
    public static final String FOLDER_NOT_EMPTY = "modeling.folder.not-empty";
    /** Папка принадлежит другой модели. */
    public static final String FOLDER_OF_OTHER_MODEL = "modeling.folder.of-other-model";
    /** Корневая папка одного вида встречается в модели больше одного раза. */
    public static final String ROOT_FOLDER_REPEATED = "modeling.folder.root-repeated";
    /** Корневую папку пытаются удалить. */
    public static final String ROOT_FOLDER_UNDELETABLE = "modeling.folder.root-undeletable";
    /** Корневую папку пытаются перенести. */
    public static final String ROOT_FOLDER_UNMOVABLE = "modeling.folder.root-unmovable";
    /** У папки задан тип вместе с родителем или ни то ни другое. */
    public static final String FOLDER_TYPE_OR_PARENT = "modeling.folder.type-or-parent";
    /** Объект переносят в папку чужого поддерева: его вид там не лежит. */
    public static final String WRONG_ROOT = "modeling.folder.wrong-root";
    /** Идентификатор, заданный клиентом для нового объекта, уже занят. */
    public static final String ID_TAKEN = "modeling.id.taken";
    /** Отпечаток запроса — не SHA-256 в шестнадцатеричной записи. */
    public static final String FINGERPRINT_NOT_SHA256 = "modeling.idempotency.fingerprint-not-sha256";
    /** Запрос с тем же ключом идемпотентности ещё выполняется. */
    public static final String IDEMPOTENT_IN_PROGRESS = "modeling.idempotency.in-progress";
    /** Ключ идемпотентности пуст или слишком длинный. */
    public static final String IDEMPOTENCY_KEY_INVALID = "modeling.idempotency.key-invalid";
    /** Ключ идемпотентности уже использован для запроса с другим содержимым. */
    public static final String IDEMPOTENCY_KEY_REUSED = "modeling.idempotency.key-reused";
    /** Внутренняя ошибка: тип объявлен в каталоге метамодели дважды. */
    public static final String CATALOG_DUPLICATE = "modeling.internal.catalog-duplicate";
    /** Внутренняя ошибка: алгоритм хеширования недоступен в JDK. */
    public static final String DIGEST_UNAVAILABLE = "modeling.internal.digest-unavailable";
    /** Внутренняя ошибка: ресурса матрицы связей нет в classpath. */
    public static final String MATRIX_RESOURCE_MISSING = "modeling.internal.matrix-missing";
    /** Внутренняя ошибка: в файле матрицы связей {@code target} стоит вне {@code source}. */
    public static final String MATRIX_TARGET_OUTSIDE_SOURCE = "modeling.internal.matrix-target-outside-source";
    /** Внутренняя ошибка: в матрице связей неизвестная буква типа связи. */
    public static final String MATRIX_UNKNOWN_LETTER = "modeling.internal.matrix-unknown-letter";
    /** Внутренняя ошибка: файл матрицы связей не разбирается. */
    public static final String MATRIX_UNREADABLE = "modeling.internal.matrix-unreadable";
    /** Недопустимое значение без подробностей — текст по умолчанию для неразборчивого ввода. */
    public static final String INVALID_VALUE = "modeling.invalid-value";
    /** Блокировка модели истекла: её нужно захватить заново. */
    public static final String LOCK_EXPIRED = "modeling.lock.expired";
    /** Срок блокировки раньше момента её захвата. */
    public static final String LOCK_EXPIRES_BEFORE_ACQUIRED = "modeling.lock.expires-before-acquired";
    /** Чужую блокировку пытается снять не администратор. */
    public static final String LOCK_FOREIGN_RELEASE = "modeling.lock.foreign-release";
    /** Модель уже заблокирована другим пользователем. */
    public static final String LOCK_HELD_BY_OTHER = "modeling.lock.held-by-other";
    /** Запись в модель без захваченной блокировки. */
    public static final String LOCK_NOT_HELD = "modeling.lock.not-held";
    /** Тип ArchiMate записан не в виде {@code archimate:Имя}. */
    public static final String ARCHI_TYPE_FORMAT = "modeling.metamodel.archi-type-format";
    /** Тип не является типом элемента ArchiMate. */
    public static final String NOT_ELEMENT_TYPE = "modeling.metamodel.not-element-type";
    /** Тип не является типом связи ArchiMate. */
    public static final String NOT_RELATIONSHIP_TYPE = "modeling.metamodel.not-relationship-type";
    /** Тип не редактируется в текущей фазе метамодели. */
    public static final String TYPE_NOT_EDITABLE = "modeling.metamodel.type-not-editable";
    /** Версия формата Archi записана не как {@code N.N.N}. */
    public static final String ARCHI_VERSION_FORMAT = "modeling.model.archi-version-format";
    /** Модель удалена, и выгрузка недоступна. */
    public static final String MODEL_DELETED = "modeling.model.deleted";
    /** Модель не в рабочем состоянии, и её содержимое не меняется. */
    public static final String MODEL_NOT_ACTIVE = "modeling.model.not-active";
    /** Переход жизненного цикла модели из текущего состояния не предусмотрен. */
    public static final String TRANSITION_NOT_ALLOWED = "modeling.model.transition-not-allowed";
    /** Название команды «удалить модель» — аргумент отказа перехода. */
    public static final String COMMAND_DELETE_MODEL = "modeling.model.command.delete";
    /** Название команды «восстановить модель» — аргумент отказа перехода. */
    public static final String COMMAND_RESTORE_MODEL = "modeling.model.command.restore";
    /** Название команды «уничтожить модель» — аргумент отказа перехода. */
    public static final String COMMAND_PURGE_MODEL = "modeling.model.command.purge";
    /** Название состояния «активна» — аргумент сообщений о жизненном цикле модели. */
    public static final String STATUS_ACTIVE = "modeling.model.status.active";
    /** Название состояния «удалена» — аргумент сообщений о жизненном цикле модели. */
    public static final String STATUS_DELETED = "modeling.model.status.deleted";
    /** Название состояния «уничтожена» — аргумент сообщений о жизненном цикле модели. */
    public static final String STATUS_PURGED = "modeling.model.status.purged";
    /** Подпись «элемента» — аргумент сообщений об имени. */
    public static final String NAME_OF_ELEMENT = "modeling.name-of.element";
    /** Подпись «папки» — аргумент сообщений об имени. */
    public static final String NAME_OF_FOLDER = "modeling.name-of.folder";
    /** Подпись «модели» — аргумент сообщений об имени. */
    public static final String NAME_OF_MODEL = "modeling.name-of.model";
    /** Подпись «связи» — аргумент сообщений об имени. */
    public static final String NAME_OF_RELATIONSHIP = "modeling.name-of.relationship";
    /** Подпись «представления» — аргумент сообщений об имени. */
    public static final String NAME_OF_VIEW = "modeling.name-of.view";
    /** Подпись «рабочего пространства» — аргумент сообщений об имени. */
    public static final String NAME_OF_WORKSPACE = "modeling.name-of.workspace";
    /** Имя объекта пусто. */
    public static final String NAME_EMPTY = "modeling.name.empty";
    /** Имя объекта не задано. */
    public static final String NAME_MISSING = "modeling.name.missing";
    /** Имя объекта длиннее предела. */
    public static final String NAME_TOO_LONG = "modeling.name.too-long";
    /** Объект не найден; аргумент — подпись объекта. */
    public static final String NOT_FOUND = "modeling.not-found";
    /** Объект неподдержанного типа хранится как есть и не редактируется. */
    public static final String OPAQUE_NOT_EDITABLE = "modeling.opaque.not-editable";
    /** У объекта неподдержанного типа нет исходного XML. */
    public static final String OPAQUE_WITHOUT_RESIDUE = "modeling.opaque.without-residue";
    /** Позиция в родителе не положительна. */
    public static final String SORT_ORDER_POSITIVE = "modeling.order.positive";
    /** Заголовок ответа об ошибке для статуса 400. */
    public static final String TITLE_BAD_REQUEST = "modeling.problem.bad-request";
    /** Заголовок ответа об ошибке для статуса 409. */
    public static final String TITLE_CONFLICT = "modeling.problem.conflict";
    /** Заголовок ответа об ошибке для статуса 403. */
    public static final String TITLE_FORBIDDEN = "modeling.problem.forbidden";
    /** Заголовок ответа об ошибке для статуса 410. */
    public static final String TITLE_GONE = "modeling.problem.gone";
    /** Заголовок ответа об ошибке для статуса 404. */
    public static final String TITLE_NOT_FOUND = "modeling.problem.not-found";
    /** Заголовок ответа об ошибке для статуса 503. */
    public static final String TITLE_UNAVAILABLE = "modeling.problem.unavailable";
    /** Заголовок ответа об ошибке для статуса 422. */
    public static final String TITLE_UNPROCESSABLE = "modeling.problem.unprocessable";
    /** Ключ свойства объекта длиннее предела. */
    public static final String PROPERTY_KEY_TOO_LONG = "modeling.property.key-too-long";
    /** Матрица ArchiMate не допускает связь между концами; в тексте — допустимые типы. */
    public static final String RELATION_NOT_PERMITTED = "modeling.relation.not-permitted";
    /** Вид доступа задан у связи, которая не {@code Access}. */
    public static final String ACCESS_TYPE_MISPLACED = "modeling.relationship.access-type-misplaced";
    /** Направленность задана у связи, которая не {@code Association}. */
    public static final String DIRECTED_MISPLACED = "modeling.relationship.directed-misplaced";
    /** Конец связи принадлежит другой модели. */
    public static final String END_OF_OTHER_MODEL = "modeling.relationship.end-of-other-model";
    /** Связь указана своим же концом. */
    public static final String RELATIONSHIP_SELF_END = "modeling.relationship.self-end";
    /** Удаляемый концепт ещё участвует в связях. */
    public static final String HAS_RELATIONSHIPS = "modeling.relationship.still-attached";
    /** В запросе неизвестный вид доступа связи. */
    public static final String ACCESS_TYPE_REQUESTED = "modeling.request.access-type";
    /** В строке модели вид доступа связи значением, которого нет в файловой записи Archi. */
    public static final String ROW_ACCESS_TYPE = "modeling.rows.access-type";
    /** В запросе запись списка доступа с неизвестным типом субъекта или уровнем. */
    public static final String ACL_ENTRY_FORMAT = "modeling.request.acl-entry";
    /** В запросе тип записан не в виде {@code archimate:Имя}. */
    public static final String ARCHI_TYPE_REQUESTED = "modeling.request.archi-type";
    /** В запросе неизвестный вид узла представления. */
    public static final String NODE_KIND_REQUESTED = "modeling.request.node-kind";
    /** В запросе неизвестная стратегия авторазметки. */
    public static final String LAYOUT_STRATEGY_REQUESTED = "modeling.request.layout-strategy";
    /** В запросе не задано обязательное поле. */
    public static final String FIELD_MISSING = "modeling.request.field-missing";
    /** В запросе не задано несколько обязательных полей. */
    public static final String FIELDS_MISSING = "modeling.request.fields-missing";
    /** В запросе пустой список объектов. */
    public static final String ITEMS_EMPTY = "modeling.request.items-empty";
    /** Остаток XML передан пустой строкой, а не отсутствием. */
    public static final String RESIDUE_EMPTY = "modeling.residue.empty";
    /** Цвет стиля записан не как {@code #rrggbb}. */
    public static final String COLOR_FORMAT = "modeling.style.color-format";
    /** Выравнивание текста задано значением вне набора Archi. */
    public static final String TEXT_ALIGNMENT_FORMAT = "modeling.style.text-alignment-format";
    /** Подпись «рабочее пространство» без имени — когда нет ни одного пространства. */
    public static final String ANY_WORKSPACE = "modeling.subject.any-workspace";
    /** Имя рабочего пространства, которое создаётся по умолчанию. */
    public static final String DEFAULT_WORKSPACE_NAME = "modeling.workspace.default-name";
    /** Подпись элемента с идентификатором — аргумент сообщений об отказе. */
    public static final String ELEMENT = "modeling.subject.element";
    /** Подпись элемента по его типу — аргумент сообщений об отказе. */
    public static final String ELEMENT_OF_TYPE = "modeling.subject.element-of-type";
    /** Подпись папки с идентификатором — аргумент сообщений об отказе. */
    public static final String FOLDER = "modeling.subject.folder";
    /** Подпись модели с идентификатором — аргумент сообщений об отказе. */
    public static final String MODEL = "modeling.subject.model";
    /** Подпись блокировки модели — аргумент сообщений об отказе. */
    public static final String MODEL_LOCK = "modeling.subject.model-lock";
    /** Подпись версии модели по идентификатору — аргумент сообщений об отказе. */
    public static final String MODEL_VERSION = "modeling.subject.model-version";
    /** Подпись объекта любого вида — аргумент сообщений об отказе. */
    public static final String OBJECT = "modeling.subject.object";
    /** Подпись объекта, которого нет в модели, — аргумент сообщения «не найдено». */
    public static final String OBJECT_IN_MODEL = "modeling.subject.object-in-model";
    /** Подпись связи с идентификатором — аргумент сообщений об отказе. */
    public static final String RELATIONSHIP = "modeling.subject.relationship";
    /** Подпись связи по её типу — аргумент сообщений об отказе. */
    public static final String RELATIONSHIP_OF_TYPE = "modeling.subject.relationship-of-type";
    /** Подпись версии по номеру и модели — аргумент сообщений об отказе. */
    public static final String VERSION_OF_MODEL = "modeling.subject.version-of-model";
    /** Подпись представления с идентификатором — аргумент сообщений об отказе. */
    public static final String VIEW = "modeling.subject.view";
    /** Подпись ребра представления — аргумент сообщений об отказе. */
    public static final String VIEW_EDGE = "modeling.subject.view-edge";
    /** Подпись узла представления — аргумент сообщений об отказе. */
    public static final String VIEW_NODE = "modeling.subject.view-node";
    /** Подпись рабочего пространства с идентификатором — аргумент сообщений об отказе. */
    public static final String WORKSPACE = "modeling.subject.workspace";
    /** Находка проверки: для пары типов матрица не допускает ни одной связи. */
    public static final String VALIDATION_NONE_PERMITTED = "modeling.validation.none-permitted";
    /** Находка проверки: тип хранится как есть и не редактируется. */
    public static final String VALIDATION_OPAQUE_ELEMENT = "modeling.validation.opaque-element";
    /** Подсказка находки проверки со списком допустимых связей. */
    public static final String VALIDATION_PERMITTED = "modeling.validation.permitted";
    /** Находка проверки: связь не разрешена матрицей ArchiMate. */
    public static final String VALIDATION_RELATION_NOT_PERMITTED = "modeling.validation.relation-not-permitted";
    /** Находка проверки: тип связи метамодели неизвестен. */
    public static final String VALIDATION_UNKNOWN_RELATIONSHIP = "modeling.validation.unknown-relationship";
    /** Значение поля длиннее предела; первый аргумент — подпись поля. */
    public static final String TOO_LONG = "modeling.value.too-long";
    /** Версия уже опубликована коммитом Git. */
    public static final String VERSION_ALREADY_PUBLISHED = "modeling.version.already-published";
    /** Комментарий первой версии, записанной при создании модели. */
    public static final String COMMENT_MODEL_CREATED = "modeling.version.comment.created";
    /** Комментарий версии, созданной откатом к прежней. */
    public static final String COMMENT_ROLLBACK = "modeling.version.comment.rollback";
    /** Идентификатор коммита Git — не 40 шестнадцатеричных знаков. */
    public static final String GIT_SHA_INVALID = "modeling.version.git-sha-invalid";
    /** Отпечаток содержимого версии — не SHA-256 в шестнадцатеричной записи. */
    public static final String HASH_NOT_SHA256 = "modeling.version.hash-not-sha256";
    /** Номер версии не положителен. */
    public static final String VERSION_NUMBER_POSITIVE = "modeling.version.number-positive";
    /** Предыдущая версия принадлежит другой модели. */
    public static final String VERSION_OTHER_MODEL = "modeling.version.other-model";
    /** Снимок версии не читается. */
    public static final String SNAPSHOT_CORRUPTED = "modeling.version.snapshot-corrupted";
    /** Снимок помеченной версии пытаются очистить. */
    public static final String SNAPSHOT_LABELLED = "modeling.version.snapshot-labelled";
    /** Снимок пытаются очистить, хотя без Git он единственная копия содержимого. */
    public static final String SNAPSHOT_ONLY_COPY = "modeling.version.snapshot-only-copy";
    /** Снимок версии очищен по сроку хранения, и восстановить его неоткуда. */
    public static final String SNAPSHOT_PURGED = "modeling.version.snapshot-purged";
    /** Идентификатор повторяется среди объектов одного представления. */
    public static final String ARCHI_ID_REPEATED_ON_VIEW = "modeling.view.archi-id-repeated";
    /** Идентификатор уже занят на представлении. */
    public static final String ARCHI_ID_TAKEN_ON_VIEW = "modeling.view.archi-id-taken";
    /** Тип объекта представления записан не в виде {@code префикс:Имя}. */
    public static final String DIAGRAM_TYPE_FORMAT = "modeling.view.diagram-type-format";
    /** Конец ребра не на этом представлении. */
    public static final String EDGE_END_ELSEWHERE = "modeling.view.edge-end-elsewhere";
    /** Концы ребра изображают не концы его связи. */
    public static final String EDGE_ENDS_MISMATCH = "modeling.view.edge-ends-mismatch";
    /** Связь ребра не принадлежит модели представления. */
    public static final String EDGE_RELATIONSHIP_ELSEWHERE = "modeling.view.edge-relationship-elsewhere";
    /** Ребро указано своим же концом. */
    public static final String EDGE_SELF_END = "modeling.view.edge-self-end";
    /** Вложенность узлов представления замкнулась в цикл. */
    public static final String NODE_NESTING_CYCLE = "modeling.view.nesting-cycle";
    /** Поле задано у узла вида, которому оно не положено. */
    public static final String FIELD_NOT_FOR_KIND = "modeling.view.field-not-for-kind";
    /** Узел без элемента — не группа и не заметка. */
    public static final String NODE_NOT_ANNOTATION = "modeling.view.node-not-annotation";
    /** В узел вкладывают другие, а узлы его вида их не содержат. */
    public static final String NODE_NOT_CONTAINER = "modeling.view.node-not-container";
    /** Текстовое поле задано у узла вида, у которого его нет. */
    public static final String NODE_TEXT_NOT_APPLICABLE = "modeling.view.node-text-not-applicable";
    /** Элемент узла не принадлежит модели представления. */
    public static final String NODE_ELEMENT_ELSEWHERE = "modeling.view.node-element-elsewhere";
    /** Новый узел над элементом, которого нет в модели представления. */
    public static final String ELEMENT_OF_OTHER_MODEL = "modeling.view.element-of-other-model";
    /** Конец нового ребра — узел или ребро, которых на представлении нет. */
    public static final String ENDPOINT_ELSEWHERE = "modeling.view.endpoint-elsewhere";
    /** Родитель узла не на этом представлении. */
    public static final String NODE_PARENT_ELSEWHERE = "modeling.view.node-parent-elsewhere";
    /** Узел указан своим же родителем. */
    public static final String NODE_SELF_PARENT = "modeling.view.node-self-parent";
    /** Размер узла не положителен и не равен {@code -1}. */
    public static final String NODE_SIZE = "modeling.view.node-size";
    /** Элемент задан у узла вида, который элемента не изображает. */
    public static final String NODE_WITH_ELEMENT = "modeling.view.node-with-element";
    /** Узел изображает элемент, но элемент не задан. */
    public static final String NODE_WITHOUT_ELEMENT = "modeling.view.node-without-element";
    /** Представление неподдержанного вида хранится как есть и не редактируется. */
    public static final String VIEW_NOT_EDITABLE = "modeling.view.not-editable";
    /** Представление принадлежит другой модели. */
    public static final String VIEW_OF_OTHER_MODEL = "modeling.view.of-other-model";
    /** Имя рабочего пространства пусто или слишком длинное. */
    public static final String WORKSPACE_NAME_INVALID = "modeling.workspace.name-invalid";

    private ModelingMessages() {
    }
}
