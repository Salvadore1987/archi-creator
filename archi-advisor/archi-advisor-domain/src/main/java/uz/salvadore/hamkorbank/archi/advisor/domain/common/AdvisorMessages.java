package uz.salvadore.hamkorbank.archi.advisor.domain.common;

/**
 * Ключи сообщений помощника. Текст — в {@code i18n/messages*.properties}, по языку
 * запроса; в коде — только ключ и аргументы.
 */
public final class AdvisorMessages {

    /** Роль автора не даёт права на операцию. */
    public static final String OPERATION_DENIED = "advisor.access.operation-denied";
    /** Помощник выключен администратором пространства. */
    public static final String ADVISOR_DISABLED = "advisor.admission.disabled";
    /** Месячный бюджет исчерпан; аргумент — дата, когда помощник заработает снова. */
    public static final String BUDGET_EXHAUSTED = "advisor.admission.budget-exhausted";
    /** Обращения слишком часты; аргумент — секунды до повтора. */
    public static final String RATE_LIMITED = "advisor.admission.rate-limited";
    /** Модель слишком велика для помощника даже в сокращённом виде. */
    public static final String CONTEXT_TOO_LARGE = "advisor.admission.context-too-large";
    /** Модель удалена, обращаться по ней нельзя. */
    public static final String MODEL_DELETED = "advisor.admission.model-deleted";
    /** Внешний сервис помощника недоступен. */
    public static final String UNAVAILABLE = "advisor.upstream.unavailable";
    /** Ключ идемпотентности не задан или слишком длинный. */
    public static final String IDEMPOTENCY_KEY_INVALID = "advisor.idempotency.key-invalid";
    /** Ключ идемпотентности уже использован для другого запроса. */
    public static final String IDEMPOTENCY_KEY_REUSED = "advisor.idempotency.key-reused";
    /** Запрос с этим ключом идемпотентности ещё выполняется. */
    public static final String SESSION_IN_PROGRESS = "advisor.idempotency.in-progress";
    /** Сессия в своём состоянии не допускает запрошенного перехода. */
    public static final String SESSION_TRANSITION = "advisor.session.transition";
    /** Функция плана завершилась без плана. */
    public static final String PLAN_REQUIRED = "advisor.session.plan-required";
    /** У автора обращения нет идентификатора. */
    public static final String REQUESTER_WITHOUT_SUBJECT = "advisor.requester.without-subject";
    /** Идентификатор объекта модели недопустим. */
    public static final String ARCHI_ID_INVALID = "advisor.archi-id.invalid";
    /** Расход токенов отрицателен. */
    public static final String USAGE_NEGATIVE = "advisor.usage.negative";
    /** Расход токенов больше, чем можно учесть: сумма переполнила бы счётчик. */
    public static final String USAGE_TOO_LARGE = "advisor.usage.too-large";
    /** Замечание ревью без ссылки на объект модели. */
    public static final String FINDING_WITHOUT_TARGET = "advisor.finding.without-target";
    /** Код замечания пуст или слишком длинный. */
    public static final String FINDING_CODE_INVALID = "advisor.finding.code-invalid";
    /** Текст замечания пуст или слишком длинный. */
    public static final String FINDING_TEXT_INVALID = "advisor.finding.text-invalid";
    /** Подсказка запрошена без выбранного элемента. */
    public static final String ELEMENT_REQUIRED = "advisor.request.element-required";
    /** Идентификатор элемента в запросе не похож на идентификатор Archi. */
    public static final String ELEMENT_ID_INVALID = "advisor.request.element-id-invalid";
    /** План запрошен без описания фрагмента архитектуры. */
    public static final String PROMPT_REQUIRED = "advisor.request.prompt-required";
    /** Описание для плана слишком длинное. */
    public static final String PROMPT_TOO_LONG = "advisor.request.prompt-too-long";
    /** Документация запрошена сразу по представлению и по элементу. */
    public static final String DOCUMENT_TARGET_AMBIGUOUS = "advisor.request.document-target-ambiguous";
    /** Обязательное поле запроса не задано. */
    public static final String FIELD_REQUIRED = "advisor.request.field-required";
    /** Период бюджета не в формате год-месяц. */
    public static final String PERIOD_INVALID = "advisor.budget.period-invalid";
    /** Порог предупреждения вне допустимого диапазона процентов. */
    public static final String THRESHOLD_INVALID = "advisor.budget.threshold-invalid";
    /** Месячный лимит токенов не положителен. */
    public static final String LIMIT_NOT_POSITIVE = "advisor.budget.limit-not-positive";
    /** Значение не прошло правило типа, причина не уточнена. */
    public static final String INVALID_VALUE = "advisor.invalid-value";
    /** Объект не найден; аргумент — подпись объекта. */
    public static final String NOT_FOUND = "advisor.not-found";
    /** Подпись модели в сообщениях. */
    public static final String MODEL = "advisor.object.model";
    /** Подпись представления в сообщениях. */
    public static final String VIEW = "advisor.object.view";
    /** Подпись элемента в сообщениях. */
    public static final String ELEMENT = "advisor.object.element";
    /** Подпись рабочего пространства в сообщениях. */
    public static final String WORKSPACE = "advisor.object.workspace";
    /** Заголовок ответа об ошибке: объект не найден. */
    public static final String TITLE_NOT_FOUND = "advisor.problem.title-not-found";
    /** Заголовок ответа об ошибке: конфликт. */
    public static final String TITLE_CONFLICT = "advisor.problem.title-conflict";
    /** Заголовок ответа об ошибке: нарушено правило. */
    public static final String TITLE_UNPROCESSABLE = "advisor.problem.title-unprocessable";
    /** Заголовок ответа об ошибке: нет доступа. */
    public static final String TITLE_FORBIDDEN = "advisor.problem.title-forbidden";
    /** Заголовок ответа об ошибке: слишком много обращений. */
    public static final String TITLE_TOO_MANY_REQUESTS = "advisor.problem.title-too-many-requests";
    /** Заголовок ответа об ошибке: слишком большой запрос. */
    public static final String TITLE_TOO_LARGE = "advisor.problem.title-too-large";
    /** Заголовок ответа об ошибке: сервис недоступен. */
    public static final String TITLE_UNAVAILABLE = "advisor.problem.title-unavailable";

    private AdvisorMessages() {
    }
}
