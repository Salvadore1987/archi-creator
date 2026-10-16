package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AuditEntryId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Одно обращение к помощнику — единица аудита. Создаётся до всех проверок и
 * сохраняется даже при отказе: администратор должен видеть попытки, отклонённые
 * по лимиту.
 *
 * <p>Переходы: {@code REQUESTED → STREAMING → COMPLETED}; отказ ({@code REJECTED})
 * — только из {@code REQUESTED}, то есть до обращения к внешнему API и до расхода
 * токенов; {@code FAILED} — из {@code REQUESTED} и {@code STREAMING}. Терминальная
 * сессия не меняется.
 *
 * <p>Результат — находки, план, ответ: текст, ссылки и предложения. Ни одно поле
 * не выражает изменения модели, и метода применить результат здесь нет.
 */
public final class AdvisorSession extends AggregateRoot<AdvisorSessionId> {

    /** Наибольшая длина ключа идемпотентности. */
    public static final int IDEMPOTENCY_KEY_MAX = 64;

    private final WorkspaceId workspaceId;
    private final ModelId modelId;
    private final AdvisorFunction function;
    private final ViewId viewId;
    private final String elementId;
    private final String idempotencyKey;
    private final String fingerprint;
    private final String requestedBy;
    private final Instant requestedAt;

    private SessionStatus status;
    private Integer contextSize;
    private boolean contextTruncated;
    private TokenUsage usage;
    private final List<Finding> findings;
    private ModelPlan plan;
    private AdvisorAnswer answer;
    private RejectionReason rejectionReason;
    private Instant firstTokenAt;
    private Instant finishedAt;
    private AuditEntry auditEntry;
    private final List<DomainEvent> events = new ArrayList<>();

    private AdvisorSession(AdvisorSessionId id, WorkspaceId workspaceId, ModelId modelId, AdvisorFunction function,
                           ViewId viewId, String elementId, String idempotencyKey,
                           String fingerprint, String requestedBy, Instant requestedAt, SessionStatus status,
                           Integer contextSize, boolean contextTruncated, TokenUsage usage,
                           List<Finding> findings, ModelPlan plan, AdvisorAnswer answer,
                           RejectionReason rejectionReason, Instant firstTokenAt,
                           Instant finishedAt, AuditEntry auditEntry) {
        super(id);
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.function = Objects.requireNonNull(function, "function");
        this.viewId = viewId;
        this.elementId = elementId;
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX) {
            throw new InvalidValueException(AdvisorMessages.IDEMPOTENCY_KEY_INVALID, IDEMPOTENCY_KEY_MAX);
        }
        this.idempotencyKey = idempotencyKey;
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.contextSize = contextSize;
        this.contextTruncated = contextTruncated;
        this.usage = Objects.requireNonNull(usage, "usage");
        this.findings = new ArrayList<>(findings);
        this.plan = plan;
        this.answer = answer;
        this.rejectionReason = rejectionReason;
        this.firstTokenAt = firstTokenAt;
        this.finishedAt = finishedAt;
        this.auditEntry = auditEntry;
    }

    /** Новое обращение — до проверок лимитов: отказ тоже будет записан. */
    public static AdvisorSession request(AdvisorSessionId id, WorkspaceId workspaceId, AdvisorRequest request,
                                         String idempotencyKey, String requestedBy, Instant now) {
        return new AdvisorSession(id, workspaceId, request.modelId(), request.function(), request.viewId(),
                request.elementId(), idempotencyKey, request.fingerprint(), requestedBy, now,
                SessionStatus.REQUESTED, null, false, TokenUsage.NONE, List.of(), null, null, null, null, null, null);
    }

    /** Сессия из хранения — как есть, без проверок переходов и без событий. */
    public static AdvisorSession restore(AdvisorSessionId id, WorkspaceId workspaceId, ModelId modelId,
                                         AdvisorFunction function, ViewId viewId,
                                         String elementId, String idempotencyKey, String fingerprint,
                                         String requestedBy, Instant requestedAt, SessionStatus status,
                                         Integer contextSize, boolean contextTruncated, TokenUsage usage,
                                         List<Finding> findings, ModelPlan plan,
                                         AdvisorAnswer answer, RejectionReason rejectionReason,
                                         Instant firstTokenAt, Instant finishedAt,
                                         AuditEntry auditEntry) {
        return new AdvisorSession(id, workspaceId, modelId, function, viewId, elementId, idempotencyKey, fingerprint,
                requestedBy, requestedAt, status, contextSize, contextTruncated, usage, findings, plan, answer,
                rejectionReason, firstTokenAt, finishedAt, auditEntry);
    }

    /** Отказ до обращения к внешнему API: токены не расходуются, аудит пишется. */
    public void reject(RejectionReason reason, AuditEntryId auditId, Instant now) {
        require(SessionStatus.REQUESTED, AdvisorMessages.COMMAND_REJECT);
        this.rejectionReason = Objects.requireNonNull(reason, "reason");
        finish(SessionStatus.REJECTED, auditId, now);
    }

    /** Внешний API принял запрос — пошёл поток. Размер контекста известен к этому моменту. */
    public void startStreaming(int contextTokens, boolean truncated) {
        require(SessionStatus.REQUESTED, AdvisorMessages.COMMAND_START_STREAMING);
        if (contextTokens < 0) {
            throw new InvalidValueException(AdvisorMessages.USAGE_NEGATIVE);
        }
        this.contextSize = contextTokens;
        this.contextTruncated = truncated;
        this.status = SessionStatus.STREAMING;
    }

    /** Первые токены ответа — для контроля времени до первого токена; повторно не перезаписывается. */
    public void firstToken(Instant now) {
        require(SessionStatus.STREAMING, AdvisorMessages.COMMAND_FIRST_TOKEN);
        if (firstTokenAt == null) {
            firstTokenAt = now;
        }
    }

    /** Находка ревью по ходу потока. У прочих функций находок нет. */
    public void addFinding(Finding finding) {
        require(SessionStatus.STREAMING, AdvisorMessages.COMMAND_ADD_FINDING);
        if (function != AdvisorFunction.REVIEW) {
            throw new IllegalSessionTransitionException(status, Message.of(AdvisorMessages.COMMAND_ADD_FINDING));
        }
        findings.add(Objects.requireNonNull(finding, "finding"));
    }

    /** Ответ получен целиком. План обязателен у функции плана, ответ — у подсказки и документации. */
    public void complete(TokenUsage usage, ModelPlan plan, AdvisorAnswer answer,
                         AuditEntryId auditId, Instant now) {
        require(SessionStatus.STREAMING, AdvisorMessages.COMMAND_COMPLETE);
        if (function == AdvisorFunction.PLAN && plan == null) {
            throw new InvalidValueException(AdvisorMessages.PLAN_REQUIRED);
        }
        this.usage = Objects.requireNonNull(usage, "usage");
        this.plan = function == AdvisorFunction.PLAN ? plan : null;
        this.answer = function == AdvisorFunction.EXPLAIN || function == AdvisorFunction.DOCUMENT
                ? answer : null;
        finish(SessionStatus.COMPLETED, auditId, now);
    }

    /** Внешний API не ответил или оборвал поток. Израсходованное учитывается всё равно. */
    public void fail(TokenUsage usage, AuditEntryId auditId, Instant now) {
        if (status != SessionStatus.REQUESTED && status != SessionStatus.STREAMING) {
            throw new IllegalSessionTransitionException(status, Message.of(AdvisorMessages.COMMAND_FAIL));
        }
        this.usage = Objects.requireNonNull(usage, "usage");
        finish(SessionStatus.FAILED, auditId, now);
    }

    private void finish(SessionStatus outcome, AuditEntryId auditId, Instant now) {
        this.status = outcome;
        this.finishedAt = now;
        this.auditEntry = new AuditEntry(auditId, requestedBy, function, usage, outcome, rejectionReason, now);
        events.add(new AdvisorSessionCompletedEvent(id(), workspaceId, modelId, function, outcome, rejectionReason,
                usage.promptTokens(), usage.completionTokens(), contextTruncated, findings.size(),
                Duration.between(requestedAt, now).toMillis(), requestedBy, now));
    }

    private void require(SessionStatus expected, String commandKey) {
        if (status != expected) {
            throw new IllegalSessionTransitionException(status, Message.of(commandKey));
        }
    }

    /** События, накопленные с последнего вызова; список очищается. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    /** Повтор того же запроса — тот же отпечаток: результат существующей сессии, без нового обращения. */
    public boolean sameRequest(AdvisorRequest request) {
        return fingerprint.equals(request.fingerprint());
    }


    /** Пространство, в бюджет которого идёт расход. */
    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    /** Модель, о которой спрошено. */
    public ModelId modelId() {
        return modelId;
    }

    /** Функция помощника. */
    public AdvisorFunction function() {
        return function;
    }

    /** Представление, если область — одно представление; {@code null} — область не представление. */
    public ViewId viewId() {
        return viewId;
    }

    /** Элемент, {@code archiId}, если область — элемент; {@code null} — область не элемент. */
    public String elementId() {
        return elementId;
    }

    /** Ключ идемпотентности, переданный клиентом. */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** Отпечаток параметров запроса — отличает повтор от конфликта. */
    public String fingerprint() {
        return fingerprint;
    }

    /** {@code subject} автора обращения. */
    public String requestedBy() {
        return requestedBy;
    }

    /** Когда обращение поступило. */
    public Instant requestedAt() {
        return requestedAt;
    }

    /** Текущее состояние. */
    public SessionStatus status() {
        return status;
    }

    /** Размер контекста в токенах; {@code null}, пока поток не начался. */
    public Integer contextSize() {
        return contextSize;
    }

    /** Пришлось ли усечь контекст модели. */
    public boolean contextTruncated() {
        return contextTruncated;
    }

    /** Израсходованные токены; у отклонённой сессии — ноль. */
    public TokenUsage usage() {
        return usage;
    }

    /** Замечания ревью в порядке поступления. */
    public List<Finding> findings() {
        return List.copyOf(findings);
    }

    /** План — у завершённой функции плана; у прочих {@code null}. */
    public ModelPlan plan() {
        return plan;
    }

    /** Ответ — у завершённых подсказки и документации; у прочих {@code null}. */
    public AdvisorAnswer answer() {
        return answer;
    }

    /** Причина отказа — у отклонённой сессии; у прочих {@code null}. */
    public RejectionReason rejectionReason() {
        return rejectionReason;
    }

    /** Когда пришёл первый токен ответа; {@code null} — ещё не пришёл. */
    public Instant firstTokenAt() {
        return firstTokenAt;
    }

    /** Когда сессия стала терминальной; {@code null} — ещё не стала. */
    public Instant finishedAt() {
        return finishedAt;
    }

    /** Запись аудита — есть ровно тогда, когда сессия терминальна; иначе {@code null}. */
    public AuditEntry auditEntry() {
        return auditEntry;
    }
}
