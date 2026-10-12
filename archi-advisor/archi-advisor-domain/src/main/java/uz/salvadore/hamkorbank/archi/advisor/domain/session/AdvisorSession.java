package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
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
public final class AdvisorSession {

    public static final int IDEMPOTENCY_KEY_MAX = 64;

    private final AdvisorSessionId id;
    private final WorkspaceId workspaceId;
    private final ModelId modelId;
    private final AdvisorFunction function;
    private final Optional<ViewId> viewId;
    private final Optional<String> elementId;
    private final String idempotencyKey;
    private final String fingerprint;
    private final String requestedBy;
    private final Instant requestedAt;

    private SessionStatus status;
    private Optional<Integer> contextSize;
    private boolean contextTruncated;
    private TokenUsage usage;
    private final List<Finding> findings;
    private Optional<ModelPlan> plan;
    private Optional<AdvisorAnswer> answer;
    private Optional<RejectionReason> rejectionReason;
    private Optional<Instant> firstTokenAt;
    private Optional<Instant> finishedAt;
    private Optional<AuditEntry> auditEntry;
    private final List<DomainEvent> events = new ArrayList<>();

    private AdvisorSession(AdvisorSessionId id, WorkspaceId workspaceId, ModelId modelId, AdvisorFunction function,
                           Optional<ViewId> viewId, Optional<String> elementId, String idempotencyKey,
                           String fingerprint, String requestedBy, Instant requestedAt, SessionStatus status,
                           Optional<Integer> contextSize, boolean contextTruncated, TokenUsage usage,
                           List<Finding> findings, Optional<ModelPlan> plan, Optional<AdvisorAnswer> answer,
                           Optional<RejectionReason> rejectionReason, Optional<Instant> firstTokenAt,
                           Optional<Instant> finishedAt, Optional<AuditEntry> auditEntry) {
        this.id = Objects.requireNonNull(id, "id");
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
                SessionStatus.REQUESTED, Optional.empty(), false, TokenUsage.NONE, List.of(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Сессия из хранения — как есть, без проверок переходов и без событий. */
    public static AdvisorSession restore(AdvisorSessionId id, WorkspaceId workspaceId, ModelId modelId,
                                         AdvisorFunction function, Optional<ViewId> viewId,
                                         Optional<String> elementId, String idempotencyKey, String fingerprint,
                                         String requestedBy, Instant requestedAt, SessionStatus status,
                                         Optional<Integer> contextSize, boolean contextTruncated, TokenUsage usage,
                                         List<Finding> findings, Optional<ModelPlan> plan,
                                         Optional<AdvisorAnswer> answer, Optional<RejectionReason> rejectionReason,
                                         Optional<Instant> firstTokenAt, Optional<Instant> finishedAt,
                                         Optional<AuditEntry> auditEntry) {
        return new AdvisorSession(id, workspaceId, modelId, function, viewId, elementId, idempotencyKey, fingerprint,
                requestedBy, requestedAt, status, contextSize, contextTruncated, usage, findings, plan, answer,
                rejectionReason, firstTokenAt, finishedAt, auditEntry);
    }

    /** Отказ до обращения к внешнему API: токены не расходуются, аудит пишется. */
    public void reject(RejectionReason reason, AuditEntryId auditId, Instant now) {
        require(SessionStatus.REQUESTED, "reject");
        this.rejectionReason = Optional.of(Objects.requireNonNull(reason, "reason"));
        finish(SessionStatus.REJECTED, auditId, now);
    }

    /** Внешний API принял запрос — пошёл поток. Размер контекста известен к этому моменту. */
    public void startStreaming(int contextTokens, boolean truncated) {
        require(SessionStatus.REQUESTED, "startStreaming");
        if (contextTokens < 0) {
            throw new InvalidValueException(AdvisorMessages.USAGE_NEGATIVE);
        }
        this.contextSize = Optional.of(contextTokens);
        this.contextTruncated = truncated;
        this.status = SessionStatus.STREAMING;
    }

    /** Первые токены ответа — для контроля времени до первого токена; повторно не перезаписывается. */
    public void firstToken(Instant now) {
        require(SessionStatus.STREAMING, "firstToken");
        if (firstTokenAt.isEmpty()) {
            firstTokenAt = Optional.of(now);
        }
    }

    /** Находка ревью по ходу потока. У прочих функций находок нет. */
    public void addFinding(Finding finding) {
        require(SessionStatus.STREAMING, "addFinding");
        if (function != AdvisorFunction.REVIEW) {
            throw new IllegalSessionTransitionException(status, "addFinding:" + function.name());
        }
        findings.add(Objects.requireNonNull(finding, "finding"));
    }

    /** Ответ получен целиком. План обязателен у функции плана, ответ — у подсказки и документации. */
    public void complete(TokenUsage usage, Optional<ModelPlan> plan, Optional<AdvisorAnswer> answer,
                         AuditEntryId auditId, Instant now) {
        require(SessionStatus.STREAMING, "complete");
        if (function == AdvisorFunction.PLAN && plan.isEmpty()) {
            throw new InvalidValueException(AdvisorMessages.PLAN_REQUIRED);
        }
        this.usage = Objects.requireNonNull(usage, "usage");
        this.plan = function == AdvisorFunction.PLAN ? plan : Optional.empty();
        this.answer = function == AdvisorFunction.EXPLAIN || function == AdvisorFunction.DOCUMENT
                ? answer : Optional.empty();
        finish(SessionStatus.COMPLETED, auditId, now);
    }

    /** Внешний API не ответил или оборвал поток. Израсходованное учитывается всё равно. */
    public void fail(TokenUsage usage, AuditEntryId auditId, Instant now) {
        if (status != SessionStatus.REQUESTED && status != SessionStatus.STREAMING) {
            throw new IllegalSessionTransitionException(status, "fail");
        }
        this.usage = Objects.requireNonNull(usage, "usage");
        finish(SessionStatus.FAILED, auditId, now);
    }

    private void finish(SessionStatus outcome, AuditEntryId auditId, Instant now) {
        this.status = outcome;
        this.finishedAt = Optional.of(now);
        this.auditEntry = Optional.of(new AuditEntry(auditId, requestedBy, function, usage, outcome,
                rejectionReason, now));
        events.add(new AdvisorSessionCompleted(id, workspaceId, modelId, function, outcome, rejectionReason,
                usage.promptTokens(), usage.completionTokens(), contextTruncated, findings.size(),
                Duration.between(requestedAt, now).toMillis(), requestedBy, now));
    }

    private void require(SessionStatus expected, String transition) {
        if (status != expected) {
            throw new IllegalSessionTransitionException(status, transition);
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

    public AdvisorSessionId id() {
        return id;
    }

    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public ModelId modelId() {
        return modelId;
    }

    public AdvisorFunction function() {
        return function;
    }

    public Optional<ViewId> viewId() {
        return viewId;
    }

    public Optional<String> elementId() {
        return elementId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public SessionStatus status() {
        return status;
    }

    public Optional<Integer> contextSize() {
        return contextSize;
    }

    public boolean contextTruncated() {
        return contextTruncated;
    }

    public TokenUsage usage() {
        return usage;
    }

    public List<Finding> findings() {
        return List.copyOf(findings);
    }

    public Optional<ModelPlan> plan() {
        return plan;
    }

    public Optional<AdvisorAnswer> answer() {
        return answer;
    }

    public Optional<RejectionReason> rejectionReason() {
        return rejectionReason;
    }

    public Optional<Instant> firstTokenAt() {
        return firstTokenAt;
    }

    public Optional<Instant> finishedAt() {
        return finishedAt;
    }

    /** Запись аудита — есть ровно тогда, когда сессия терминальна. */
    public Optional<AuditEntry> auditEntry() {
        return auditEntry;
    }
}
