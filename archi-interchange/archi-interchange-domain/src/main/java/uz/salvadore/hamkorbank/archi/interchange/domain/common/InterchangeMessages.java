package uz.salvadore.hamkorbank.archi.interchange.domain.common;

/**
 * Ключи сообщений interchange. Текст — в {@code i18n/messages*.properties}, по языку запроса;
 * в коде — только ключ и аргументы.
 */
public final class InterchangeMessages {

    /** Название команды «разобрать файл импорта». */
    public static final String COMMAND_PARSE_IMPORT = "interchange.command.parse-import";
    /** Название команды «проверить импорт». */
    public static final String COMMAND_VALIDATE_IMPORT = "interchange.command.validate-import";
    /** Название команды «отклонить импорт». */
    public static final String COMMAND_REJECT_IMPORT = "interchange.command.reject-import";
    /** Название команды «применить импорт». */
    public static final String COMMAND_APPLY_IMPORT = "interchange.command.apply-import";
    /** Название команды «собрать выгрузку». */
    public static final String COMMAND_RENDER_EXPORT = "interchange.command.render-export";
    /** Название команды «отдать выгрузку». */
    public static final String COMMAND_DELIVER_EXPORT = "interchange.command.deliver-export";
    /** Название команды «отметить сбой выгрузки». */
    public static final String COMMAND_FAIL_EXPORT = "interchange.command.fail-export";
    /** Состояние сессии импорта «получен». */
    public static final String STATUS_IMPORT_RECEIVED = "interchange.status.import.received";
    /** Состояние сессии импорта «разобран». */
    public static final String STATUS_IMPORT_PARSED = "interchange.status.import.parsed";
    /** Состояние сессии импорта «проверен». */
    public static final String STATUS_IMPORT_VALIDATED = "interchange.status.import.validated";
    /** Состояние сессии импорта «применён». */
    public static final String STATUS_IMPORT_APPLIED = "interchange.status.import.applied";
    /** Состояние сессии импорта «отклонён». */
    public static final String STATUS_IMPORT_REJECTED = "interchange.status.import.rejected";
    /** Состояние задания выгрузки «запрошена». */
    public static final String STATUS_EXPORT_REQUESTED = "interchange.status.export.requested";
    /** Состояние задания выгрузки «собрана». */
    public static final String STATUS_EXPORT_RENDERED = "interchange.status.export.rendered";
    /** Состояние задания выгрузки «выдана». */
    public static final String STATUS_EXPORT_DELIVERED = "interchange.status.export.delivered";
    /** Состояние задания выгрузки «сбой». */
    public static final String STATUS_EXPORT_FAILED = "interchange.status.export.failed";
    /** Название формата выгрузки .archimate. */
    public static final String FORMAT_ARCHIMATE = "interchange.format.archimate";
    /** Название формата выгрузки Open Exchange. */
    public static final String FORMAT_OPEN_EXCHANGE = "interchange.format.open-exchange";
    /** Название формата выгрузки — каталог CSV. */
    public static final String FORMAT_CSV_CATALOG = "interchange.format.csv-catalog";
    /** Название формата выгрузки SVG. */
    public static final String FORMAT_SVG = "interchange.format.svg";
    /** Название формата выгрузки PNG. */
    public static final String FORMAT_PNG = "interchange.format.png";
    /** Название формата — разложенный YAML для Git. */
    public static final String FORMAT_GIT_YAML = "interchange.format.git-yaml";
    /** Подпись отсутствующего значения в тексте отказа. */
    public static final String VALUE_MISSING = "interchange.value.missing";

    /** Значение атрибута или элемента содержит символы, которых не может быть в файле Archi. */
    public static final String TEXT_NOT_XML = "interchange.document.text-not-xml";
    /** Применённый импорт записывает версию с номером меньше единицы. */
    public static final String IMPORT_VERSION_NO = "interchange.import.version-no";
    /** Узлы файла вложены глубже предела разбора. */
    public static final String NESTING_TOO_DEEP = "interchange.document.nesting-too-deep";

    /** Операция недоступна роли пользователя; в тексте — название операции. */
    public static final String OPERATION_DENIED = "interchange.access.operation-denied";
    /** Название операции в отказе роли: импорт файла .archimate. */
    public static final String OPERATION_IMPORT_ARCHIMATE_FILE = "interchange.operation.import-archimate-file";
    /** Название операции в отказе роли: выгрузка .archimate. */
    public static final String OPERATION_EXPORT_ARCHIMATE = "interchange.operation.export-archimate";
    /** Название операции в отказе роли: выгрузка в Open Exchange. */
    public static final String OPERATION_EXPORT_OPEN_EXCHANGE = "interchange.operation.export-open-exchange";
    /** Название операции в отказе роли: выгрузка каталога в CSV. */
    public static final String OPERATION_EXPORT_CATALOG_CSV = "interchange.operation.export-catalog-csv";
    /** Название операции в отказе роли: выгрузка картинки представления. */
    public static final String OPERATION_EXPORT_VIEW_IMAGE = "interchange.operation.export-view-image";
    /** Название операции в отказе роли: выгрузка картинок всех представлений. */
    public static final String OPERATION_EXPORT_ALL_VIEW_IMAGES = "interchange.operation.export-all-view-images";
    /** Название операции в отказе роли: настройка импорта. */
    public static final String OPERATION_CONFIGURE_IMPORT_POLICY = "interchange.operation.configure-import-policy";
    /** Название операции в отказе роли: чтение настройки импорта. */
    public static final String OPERATION_GET_IMPORT_POLICY = "interchange.operation.get-import-policy";
    /** Название операции в отказе роли: публикация в Git. */
    public static final String OPERATION_PUSH_MODEL_TO_GIT = "interchange.operation.push-model-to-git";
    /** Название операции в отказе роли: импорт из Git. */
    public static final String OPERATION_PULL_MODEL_FROM_GIT = "interchange.operation.pull-model-from-git";
    /** Название операции в отказе роли: слияние ветки Git. */
    public static final String OPERATION_MERGE_MODEL_BRANCH = "interchange.operation.merge-model-branch";
    /** Название операции в отказе роли: привязка к Git. */
    public static final String OPERATION_CONFIGURE_GIT_BINDING = "interchange.operation.configure-git-binding";
    /** Значение «нет» логического поля в выгрузке CSV. */
    public static final String CSV_NO = "interchange.csv.no";
    /** Разделитель CSV выбран не из допустимых. */
    public static final String CSV_SEPARATOR = "interchange.csv.separator";
    /** Разделитель CSV длиннее одного символа. */
    public static final String CSV_SEPARATOR_LENGTH = "interchange.csv.separator-length";
    /** Значение «да» логического поля в выгрузке CSV. */
    public static final String CSV_YES = "interchange.csv.yes";
    /** Идентификатор Archi не годится по виду. */
    public static final String ARCHI_ID_INVALID = "interchange.document.archi-id-invalid";
    /** Имя атрибута узла документа — не имя XML. */
    public static final String ATTRIBUTE_NAME_INVALID = "interchange.document.attribute-name-invalid";
    /** Атрибуты заданы не парами имя–значение. */
    public static final String ATTRIBUTE_PAIRS_EXPECTED = "interchange.document.attribute-pairs-expected";
    /** Атрибут узла документа задан дважды. */
    public static final String ATTRIBUTE_REPEATED = "interchange.document.attribute-repeated";
    /** Сводка о повреждённом файле: первый дефект, его место и число остальных. */
    public static final String CORRUPT_SUMMARY = "interchange.document.corrupt";
    /** Уточнение сводки о повреждении: номер строки файла. */
    public static final String CORRUPT_AT_LINE = "interchange.document.corrupt-at-line";
    /** Уточнение сводки о повреждении: сколько дефектов ещё найдено. */
    public static final String CORRUPT_MORE = "interchange.document.corrupt-more";
    /** Атрибут ссылается на объект, которого в документе нет. */
    public static final String DANGLING_REFERENCE = "interchange.document.dangling-reference";
    /** Фрагмент остатка XML адресован не тому узлу, внутри которого лежит. */
    public static final String FRAGMENT_MISADDRESSED = "interchange.document.fragment-misaddressed";
    /** Фрагмент остатка XML не является элементом. */
    public static final String FRAGMENT_NOT_ELEMENT = "interchange.document.fragment-not-element";
    /** Фрагмент в корне модели адресован какому-то узлу. */
    public static final String FRAGMENT_ROOT_MISADDRESSED = "interchange.document.fragment-root-misaddressed";
    /** Отпечаток не похож на SHA-256 в шестнадцатеричной записи. */
    public static final String HASH_NOT_SHA256 = "interchange.document.hash-not-sha256";
    /** Идентификатор узла документа недопустим. */
    public static final String ID_INVALID = "interchange.document.id-invalid";
    /** Идентификатор в файле повторяется; в тексте — строка первого вхождения. */
    public static final String ID_REPEATED = "interchange.document.id-repeated";
    /** Идентификатор модели недопустим. */
    public static final String MODEL_ID_INVALID = "interchange.document.model-id-invalid";
    /** У корня модели нет идентификатора. */
    public static final String MODEL_WITHOUT_ID = "interchange.document.model-without-id";
    /** У узла документа нет идентификатора. */
    public static final String NODE_ID_MISSING = "interchange.document.node-id-missing";
    /** У элемента файла, которому положен идентификатор, его нет. */
    public static final String NODE_WITHOUT_ID = "interchange.document.node-without-id";
    /** У элемента файла нет атрибута {@code xsi:type}. */
    public static final String NODE_WITHOUT_TYPE = "interchange.document.node-without-type";
    /** Корень файла — не модель Archi в своём пространстве имён. */
    public static final String NOT_ARCHIMATE_ROOT = "interchange.document.not-archimate-root";
    /** Тег не из тех, которыми задаются узлы документа. */
    public static final String NOT_DOCUMENT_NODE = "interchange.document.not-node";
    /** Тег не из тех, которыми задаются значения документа. */
    public static final String NOT_DOCUMENT_VALUE = "interchange.document.not-value";
    /** Позиция ребёнка не совпадает с его местом среди соседей. */
    public static final String ORDER_MISMATCH = "interchange.document.order-mismatch";
    /** Позиция узла в документе отрицательна. */
    public static final String ORDER_NEGATIVE = "interchange.document.order-negative";
    /** У связи нет источника или цели. */
    public static final String RELATIONSHIP_WITHOUT_END = "interchange.document.relationship-without-end";
    /** {@code targetConnections} узла не совпадает с набором входящих соединений. */
    public static final String TARGET_CONNECTIONS_DIFFER = "interchange.document.target-connections-differ";
    /** К узлу ведут соединения, а {@code targetConnections} у него не заполнен. */
    public static final String TARGET_CONNECTIONS_MISSING = "interchange.document.target-connections-missing";
    /** В файле встретился текст там, где значения быть не может. */
    public static final String TEXT_OUTSIDE_VALUE = "interchange.document.text-outside-value";
    /** Отпечаток содержимого модели в запросе недопустим по виду. */
    public static final String GIT_BASE_HASH_INVALID = "interchange.git.base-hash-invalid";
    /** Присланный коммит не общий предок коммита ссылки — отчёт слияния устарел. */
    public static final String GIT_MERGE_BASE_INVALID = "interchange.git.merge-base-invalid";
    /** Комментарий версии, созданной слиянием из Git: ссылка и коммит. */
    public static final String GIT_MERGE_COMMENT = "interchange.git.merge-comment";
    /** Слияние отклонено: состояние модели в коммите не собирается. */
    public static final String GIT_MERGE_REJECTED_CORRUPT = "interchange.git.merge-rejected-corrupt";
    /** Выбор по конфликту слияния — не «наше» и не «их». */
    public static final String GIT_MERGE_RESOLUTION_INVALID = "interchange.git.merge-resolution-invalid";
    /** Выбор прислан для конфликтов, которых в слиянии нет. */
    public static final String GIT_MERGE_RESOLUTION_UNKNOWN = "interchange.git.merge-resolution-unknown";
    /** Слияние применяется, а по части конфликтов выбор не сделан. */
    public static final String GIT_MERGE_UNRESOLVED = "interchange.git.merge-unresolved";
    /** У коммита ссылки и последней опубликованной версии нет общего состояния модели. */
    public static final String GIT_NO_MERGE_BASE = "interchange.git.no-merge-base";
    /** Модель ещё не публиковалась в Git — сливать не с чем. */
    public static final String GIT_NO_PUBLISHED_VERSION = "interchange.git.no-published-version";
    /** Комментарий версии, созданной импортом из Git: ссылка и коммит. */
    public static final String GIT_PULL_COMMENT = "interchange.git.pull-comment";
    /** Находка отклонённой попытки импорта или слияния из Git; в тексте — код отказа. */
    public static final String GIT_PULL_FAILED = "interchange.git.pull-failed";
    /** В запросе импорта из Git не задано обязательное поле. */
    public static final String GIT_PULL_FIELD_MISSING = "interchange.git.pull-field-missing";
    /** Импорт по ссылке отклонён: состояние модели в коммите не собирается. */
    public static final String GIT_PULL_REJECTED_CORRUPT = "interchange.git.pull-rejected-corrupt";
    /** Строгий импорт по ссылке отклонён из-за нарушений матрицы связей. */
    public static final String GIT_PULL_REJECTED_STRICT = "interchange.git.pull-rejected-strict";
    /** В репозитории нет ветки, тега или коммита с таким именем. */
    public static final String GIT_REF_NOT_FOUND = "interchange.git.ref-not-found";
    /** Модель изменилась после построения отчёта импорта или слияния. */
    public static final String GIT_REPORT_STALE = "interchange.git.report-stale";
    /** Автор коммитов в привязке задан без субъекта, имени или почты. */
    public static final String GIT_AUTHOR_INVALID = "interchange.git.author-invalid";
    /** Автор коммитов для одного субъекта задан в привязке дважды. */
    public static final String GIT_AUTHOR_REPEATED = "interchange.git.author-repeated";
    /** Привязка рабочего пространства к Git снята. */
    public static final String GIT_BINDING_DISABLED = "interchange.git.binding-disabled";
    /** У рабочего пространства нет рабочей привязки к Git. */
    public static final String GIT_BINDING_MISSING = "interchange.git.binding-missing";
    /** Имя ветки привязки недопустимо. */
    public static final String GIT_BRANCH_INVALID = "interchange.git.branch-invalid";
    /** Сообщение коммита, публикующего версию модели. */
    public static final String GIT_COMMIT_MESSAGE = "interchange.git.commit-message";
    /** Поле в файле YAML каталога модели не того вида. */
    public static final String GIT_FIELD_INVALID = "interchange.git.field-invalid";
    /** В файле YAML каталога модели нет обязательного поля. */
    public static final String GIT_FIELD_MISSING = "interchange.git.field-missing";
    /** В файле YAML каталога модели незнакомое поле. */
    public static final String GIT_FIELD_UNKNOWN = "interchange.git.field-unknown";
    /** В каталоге модели в Git нет обязательного файла. */
    public static final String GIT_FILE_MISSING = "interchange.git.file-missing";
    /** Файл каталога модели не читается как YAML. */
    public static final String GIT_FILE_UNREADABLE = "interchange.git.file-unreadable";
    /** Объект в YAML ссылается на папку, которой нет в описании папок. */
    public static final String GIT_FOLDER_UNKNOWN = "interchange.git.folder-unknown";
    /** Версия формата каталога модели в Git не поддерживается. */
    public static final String GIT_FORMAT_VERSION = "interchange.git.format-version";
    /** Идентификатор в файлах YAML каталога модели повторяется. */
    public static final String GIT_ID_REPEATED = "interchange.git.id-repeated";
    /** В коммите нет каталога модели. */
    public static final String GIT_MODEL_NOT_IN_COMMIT = "interchange.git.model-not-in-commit";
    /** Позиция объекта в YAML уже занята соседом. */
    public static final String GIT_POSITION_REPEATED = "interchange.git.position-repeated";
    /** Ссылка Git — ветка, тег или коммит — недопустима по виду. */
    public static final String GIT_REF_INVALID = "interchange.git.ref-invalid";
    /** Секрет доступа к репозиторию на сервере не задан. */
    public static final String GIT_SECRET_MISSING = "interchange.git.secret-missing";
    /** Ссылка на секрет доступа задана не в виде {@code env:ИМЯ}. */
    public static final String GIT_SECRET_REF_INVALID = "interchange.git.secret-ref-invalid";
    /** Идентификатор коммита недопустим по виду. */
    public static final String GIT_SHA_INVALID = "interchange.git.sha-invalid";
    /** Снимок версии, восстановленный из коммита, не совпал с отпечатком версии. */
    public static final String GIT_SNAPSHOT_MISMATCH = "interchange.git.snapshot-mismatch";
    /** Репозиторий не отвечает или отказывает в доступе. */
    public static final String GIT_UNREACHABLE = "interchange.git.unreachable";
    /** Адрес репозитория в привязке недопустим. */
    public static final String GIT_URL_INVALID = "interchange.git.url-invalid";
    /** В адресе репозитория учётные данные; сам адрес в текст не попадает. */
    public static final String GIT_URL_CREDENTIALS = "interchange.git.url-credentials";
    /** Адрес репозитория ведёт в файловую систему сервера, а это не разрешено настройкой. */
    public static final String GIT_URL_LOCAL = "interchange.git.url-local";
    /** Потеря в картинке: конец связи не найден на представлении, связь не нарисована. */
    public static final String IMAGE_LOSS_EDGE = "interchange.image.loss-edge";
    /** Потеря в картинке: шрифта объекта нет на сервере, подпись напечатана шрифтом по умолчанию. */
    public static final String IMAGE_LOSS_FONT = "interchange.image.loss-font";
    /** Потеря в картинке: изображение узла не переносится. */
    public static final String IMAGE_LOSS_IMAGE = "interchange.image.loss-image";
    /** Потеря в картинке: подпись не поместилась в фигуру и усечена. */
    public static final String IMAGE_LOSS_LABEL = "interchange.image.loss-label";
    /** Потеря в картинке: неподдержанный тип нарисован силуэтом заглушки. */
    public static final String IMAGE_LOSS_PLACEHOLDER = "interchange.image.loss-placeholder";
    /** Потеря в картинке: значение цвета из файла не по правилу формата, нарисовано цветом слоя. */
    public static final String IMAGE_LOSS_STYLE = "interchange.image.loss-style";
    /** Подзаголовок фигуры неподдержанного типа на картинке. */
    public static final String IMAGE_OPAQUE_SUBTITLE = "interchange.image.opaque-subtitle";
    /** Параметр выгрузки картинки задан недопустимым значением. */
    public static final String IMAGE_OPTION_INVALID = "interchange.image.option-invalid";
    /** Картинка при заданной плотности больше предела по стороне. */
    public static final String IMAGE_TOO_LARGE = "interchange.image.too-large";
    /** Картинка при заданной плотности больше предела по площади. */
    public static final String IMAGE_AREA_TOO_LARGE = "interchange.image.area-too-large";
    /** Растеризатор PNG занят дольше допустимого ожидания. */
    public static final String RASTERIZER_BUSY = "interchange.image.rasterizer-busy";
    /** Представления нет в зафиксированной версии, из которой строится картинка. */
    public static final String IMAGE_VIEW_NOT_IN_VERSION = "interchange.image.view-not-in-version";
    /** Папки, которой ограничен каталог CSV, нет в выгружаемой версии. */
    public static final String CSV_FOLDER_NOT_IN_VERSION = "interchange.csv.folder-not-in-version";
    /** Выгрузка в формат с потерями собрана без отчёта о потерях. */
    public static final String EXPORT_LOSS_REPORT_MISSING = "interchange.export.loss-report-missing";
    /** Запись отчёта о потерях называет неизвестный вид объекта. */
    public static final String LOSS_UNKNOWN_KIND = "interchange.export.loss-unknown-kind";
    /** Запись отчёта о потерях без описания или без причины. */
    public static final String LOSS_WITHOUT_REASON = "interchange.export.loss-without-reason";
    /** Формат без потерь, а отчёт выгрузки перечисляет потери. */
    public static final String EXPORT_LOSSLESS_WITH_LOSSES = "interchange.export.lossless-with-losses";
    /** Масштаб выгрузки не положителен. */
    public static final String EXPORT_SCALE_POSITIVE = "interchange.export.scale-positive";
    /** Задание выгрузки получило недопустимый из своего состояния переход. */
    public static final String EXPORT_TRANSITION = "interchange.export.transition";
    /** Запрошен неизвестный формат выгрузки. */
    public static final String UNKNOWN_FORMAT = "interchange.export.unknown-format";
    /** Артефакт собран не из той версии, что зафиксировало задание выгрузки. */
    public static final String EXPORT_VERSION_MISMATCH = "interchange.export.version-mismatch";
    /** Номер версии для выгрузки не положителен. */
    public static final String EXPORT_VERSION_POSITIVE = "interchange.export.version-positive";
    /** Задание выгрузки завершают без артефакта. */
    public static final String EXPORT_WITHOUT_ARTIFACT = "interchange.export.without-artifact";
    /** Находка импорта создаётся без кода. */
    public static final String FINDING_WITHOUT_CODE = "interchange.import.finding-without-code";
    /** Ключ идемпотентности пуст или слишком длинный. */
    public static final String IDEMPOTENCY_KEY_INVALID = "interchange.import.idempotency-key-invalid";
    /** Импорт с тем же ключом идемпотентности уже выполняется. */
    public static final String IMPORT_IN_PROGRESS = "interchange.import.in-progress";
    /** Ключ идемпотентности уже использован для другого файла. */
    public static final String IMPORT_KEY_REUSED = "interchange.import.key-reused";
    /** Файл разобран, но модель из него не собирается; в тексте — причина. */
    public static final String IMPORT_CONTENT_REJECTED = "interchange.import.content-rejected";
    /** Модель из файла собрана, но не записана; в тексте — причина отказа modeling. */
    public static final String IMPORT_APPLY_FAILED = "interchange.import.apply-failed";
    /** В рабочее пространство уже идёт другой импорт. */
    public static final String IMPORT_WORKSPACE_BUSY = "interchange.import.workspace-busy";
    /** Импорт файла отклонён: файл повреждён. */
    public static final String REJECTED_CORRUPT = "interchange.import.rejected-corrupt";
    /** Строгий импорт файла отклонён из-за нарушений матрицы связей. */
    public static final String REJECTED_STRICT = "interchange.import.rejected-strict";
    /** Находка импорта: связь между этими типами не разрешена; в тексте — допустимые виды. */
    public static final String RELATION_NOT_PERMITTED = "interchange.import.relation-not-permitted";
    /** Подан пустой файл для импорта. */
    public static final String SOURCE_EMPTY = "interchange.import.source-empty";
    /** Имя импортируемого файла пусто или слишком длинное. */
    public static final String SOURCE_NAME_INVALID = "interchange.import.source-name-invalid";
    /** Сессия импорта получила недопустимый из своего состояния переход. */
    public static final String IMPORT_TRANSITION = "interchange.import.transition";
    /** Находка импорта: тип неизвестен метамодели, объект сохранён как есть и не редактируется. */
    public static final String UNKNOWN_TYPE_STORED = "interchange.import.unknown-type";
    /** Отказ о повреждённом файле создаётся без единого дефекта. */
    public static final String CORRUPT_WITHOUT_DEFECTS = "interchange.internal.corrupt-without-defects";
    /** Алгоритм отпечатка недоступен в JDK сервера. */
    public static final String DIGEST_UNAVAILABLE = "interchange.internal.digest-unavailable";
    /** Потеря в OEF: атрибут объекта в формате не выражается. */
    public static final String OEF_LOSS_ATTRIBUTE = "interchange.oef.loss-attribute";
    /** Потеря в OEF: конец линии не нашёлся на представлении, точки перегиба не выгружены. */
    public static final String OEF_LOSS_BENDPOINTS = "interchange.oef.loss-bendpoints";
    /** Потеря в OEF: типа объекта в формате нет, объект не выгружен. */
    public static final String OEF_LOSS_CONCEPT_TYPE = "interchange.oef.loss-concept-type";
    /** Потеря в OEF: связь модели не выгружена, поэтому и линия на представлении. */
    public static final String OEF_LOSS_EDGE_CONCEPT = "interchange.oef.loss-edge-concept";
    /** Потеря в OEF: конец линии не выгружен, поэтому и линия. */
    public static final String OEF_LOSS_EDGE_ENDPOINT = "interchange.oef.loss-edge-endpoint";
    /** Потеря в OEF: линия такого вида в формате не выражается. */
    public static final String OEF_LOSS_EDGE_TYPE = "interchange.oef.loss-edge-type";
    /** Потеря в OEF: конец связи не выгружен, поэтому и связь. */
    public static final String OEF_LOSS_ENDPOINT = "interchange.oef.loss-endpoint";
    /** Потеря в OEF: тип корневой папки не выражается. */
    public static final String OEF_LOSS_FOLDER_TYPE = "interchange.oef.loss-folder-type";
    /** Потеря в OEF: идентификатор не годится для XML ID и заменён. */
    public static final String OEF_LOSS_IDENTIFIER = "interchange.oef.loss-identifier";
    /** Потеря в OEF: элемент объекта не выгружен, поэтому и объект на представлении. */
    public static final String OEF_LOSS_NODE_CONCEPT = "interchange.oef.loss-node-concept";
    /** Потеря в OEF: объект представления такого вида в формате не выражается. */
    public static final String OEF_LOSS_NODE_TYPE = "interchange.oef.loss-node-type";
    /** Потеря в OEF: у объекта такого вида в формате нет свойств. */
    public static final String OEF_LOSS_PROPERTY = "interchange.oef.loss-property";
    /** Потеря в OEF: фрагмент остатка XML в формате не выражается. */
    public static final String OEF_LOSS_RAW = "interchange.oef.loss-raw";
    /** Потеря в OEF: представление сдвинуто, потому что координаты в формате неотрицательны. */
    public static final String OEF_LOSS_SHIFT = "interchange.oef.loss-shift";
    /** Потеря в OEF: значение стиля не разобрано и не выгружено. */
    public static final String OEF_LOSS_STYLE = "interchange.oef.loss-style";
    /** Потеря в OEF: содержимое объекта в формате не выражается. */
    public static final String OEF_LOSS_VALUE = "interchange.oef.loss-value";
    /** Потеря в OEF: представление, на которое ссылается объект, не выгружено. */
    public static final String OEF_LOSS_VIEW_REF = "interchange.oef.loss-view-ref";
    /** Потеря в OEF: представление такого вида в формате не выражается. */
    public static final String OEF_LOSS_VIEW_TYPE = "interchange.oef.loss-view-type";
    /** Выгрузка в OEF не прошла проверку схемой и не отдана — ошибка сервера. */
    public static final String OEF_SCHEMA_VIOLATION = "interchange.oef.schema-violation";
    /** Ребро представления ссылается на объект вне модели. */
    public static final String EDGE_OUTSIDE = "interchange.mapping.edge-outside";
    /** Связь ссылается на элемент вне модели. */
    public static final String RELATIONSHIP_OUTSIDE = "interchange.mapping.relationship-outside";
    /** Корневая папка модели без типа Archi. */
    public static final String ROOT_FOLDER_UNTYPED = "interchange.mapping.root-folder-untyped";
    /** Узел документа встретился там, где такого быть не может; в тексте — где. */
    public static final String UNEXPECTED_NODE = "interchange.mapping.unexpected-node";
    /** Уточнение места неожиданного узла: в папке. */
    public static final String WHERE_FOLDER = "interchange.mapping.where.folder";
    /** Уточнение места неожиданного узла: внутри другого узла. */
    public static final String WHERE_INSIDE = "interchange.mapping.where.inside";
    /** Уточнение места неожиданного узла: в корне модели. */
    public static final String WHERE_MODEL_ROOT = "interchange.mapping.where.model-root";
    /** Уточнение места неожиданного узла: в представлении. */
    public static final String WHERE_VIEW = "interchange.mapping.where.view";
    /** Уточнение места неожиданного узла: в ребре представления. */
    public static final String WHERE_VIEW_EDGE = "interchange.mapping.where.view-edge";
    /** Уточнение места неожиданного узла: в узле представления. */
    public static final String WHERE_VIEW_NODE = "interchange.mapping.where.view-node";
    /** Уточнение к неожиданному узлу: у него нет обязательного атрибута. */
    public static final String WHERE_WITHOUT = "interchange.mapping.where.without";
    /** Заголовок ответа об ошибке, когда отказ вызван содержимым файла. */
    public static final String TITLE_BAD_FILE = "interchange.problem.bad-file";
    /** Заголовок ответа об ошибке импорта и выгрузки в остальных случаях. */
    public static final String TITLE_INTERCHANGE = "interchange.problem.interchange";
    /** Остаток XML в базе не разбирается. */
    public static final String RESIDUE_CORRUPTED = "interchange.residue.corrupted";
    /** В остатке XML на этом месте ожидалась запись другого вида. */
    public static final String RESIDUE_RECORD_EXPECTED = "interchange.residue.record-expected";
    /** У записи остатка XML нет обязательного атрибута. */
    public static final String RESIDUE_ATTRIBUTE_MISSING = "interchange.residue.attribute-missing";
    /** В остатке XML встретилась неизвестная запись. */
    public static final String RESIDUE_UNKNOWN_RECORD = "interchange.residue.unknown-record";
    /** У нетипизированного значения остатка нашёлся небуквальный атрибут. */
    public static final String UNTYPED_VALUE_LITERAL = "interchange.residue.untyped-value-literal";
    /** Остаток XML не удалось записать. */
    public static final String RESIDUE_UNWRITABLE = "interchange.residue.unwritable";
    /** Обёртка для текста, сохранённого уже переведённым: выводится как есть. */
    public static final String STORED_TEXT = "interchange.stored-text";
    /** Комментарий версии, созданной импортом файла. */
    public static final String COMMENT_IMPORTED = "interchange.version.comment.imported";
    /** Рабочее пространство не найдено. */
    public static final String WORKSPACE_NOT_FOUND = "interchange.workspace.not-found";
    /** У модели не объявлено пространство имён, нужное писателю. */
    public static final String NAMESPACE_MISSING = "interchange.writer.namespace-missing";
    /** Файл не разбирается как XML, а подробности разборщик не дал. */
    public static final String XML_UNREADABLE_NO_DETAIL = "interchange.xml.unreadable-no-detail";

    private InterchangeMessages() {
    }
}
