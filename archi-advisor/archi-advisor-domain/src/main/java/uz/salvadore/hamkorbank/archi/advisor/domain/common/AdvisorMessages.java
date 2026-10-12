package uz.salvadore.hamkorbank.archi.advisor.domain.common;

/**
 * Ключи сообщений помощника. Текст — в {@code i18n/messages*.properties}, по языку
 * запроса; в коде — только ключ и аргументы.
 */
public final class AdvisorMessages {

    public static final String OPERATION_DENIED = "advisor.access.operation-denied";
    public static final String ADVISOR_DISABLED = "advisor.admission.disabled";
    public static final String BUDGET_EXHAUSTED = "advisor.admission.budget-exhausted";
    public static final String RATE_LIMITED = "advisor.admission.rate-limited";
    public static final String CONTEXT_TOO_LARGE = "advisor.admission.context-too-large";
    public static final String MODEL_DELETED = "advisor.admission.model-deleted";
    public static final String UNAVAILABLE = "advisor.upstream.unavailable";
    public static final String IDEMPOTENCY_KEY_INVALID = "advisor.idempotency.key-invalid";
    public static final String IDEMPOTENCY_KEY_REUSED = "advisor.idempotency.key-reused";
    public static final String SESSION_IN_PROGRESS = "advisor.idempotency.in-progress";
    public static final String SESSION_TRANSITION = "advisor.session.transition";
    public static final String PLAN_REQUIRED = "advisor.session.plan-required";
    public static final String REQUESTER_WITHOUT_SUBJECT = "advisor.requester.without-subject";
    public static final String ARCHI_ID_INVALID = "advisor.archi-id.invalid";
    public static final String USAGE_NEGATIVE = "advisor.usage.negative";
    public static final String FINDING_WITHOUT_TARGET = "advisor.finding.without-target";
    public static final String FINDING_CODE_INVALID = "advisor.finding.code-invalid";
    public static final String FINDING_TEXT_INVALID = "advisor.finding.text-invalid";
    public static final String ELEMENT_REQUIRED = "advisor.request.element-required";
    public static final String PROMPT_REQUIRED = "advisor.request.prompt-required";
    public static final String PROMPT_TOO_LONG = "advisor.request.prompt-too-long";
    public static final String DOCUMENT_TARGET_AMBIGUOUS = "advisor.request.document-target-ambiguous";
    public static final String FIELD_REQUIRED = "advisor.request.field-required";
    public static final String PERIOD_INVALID = "advisor.budget.period-invalid";
    public static final String THRESHOLD_INVALID = "advisor.budget.threshold-invalid";
    public static final String LIMIT_NOT_POSITIVE = "advisor.budget.limit-not-positive";
    public static final String INVALID_VALUE = "advisor.invalid-value";
    public static final String NOT_FOUND = "advisor.not-found";
    public static final String MODEL = "advisor.object.model";
    public static final String VIEW = "advisor.object.view";
    public static final String ELEMENT = "advisor.object.element";
    public static final String WORKSPACE = "advisor.object.workspace";
    public static final String TITLE_NOT_FOUND = "advisor.problem.title-not-found";
    public static final String TITLE_CONFLICT = "advisor.problem.title-conflict";
    public static final String TITLE_UNPROCESSABLE = "advisor.problem.title-unprocessable";
    public static final String TITLE_FORBIDDEN = "advisor.problem.title-forbidden";
    public static final String TITLE_TOO_MANY_REQUESTS = "advisor.problem.title-too-many-requests";
    public static final String TITLE_TOO_LARGE = "advisor.problem.title-too-large";
    public static final String TITLE_UNAVAILABLE = "advisor.problem.title-unavailable";

    private AdvisorMessages() {
    }
}
