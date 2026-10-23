package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Строка сессии помощника: параметры обращения, состояние, расход и результат в столбце {@code result}. */
@Entity
@Table(name = "advisor_session")
public class AdvisorSessionEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    public UUID workspaceId;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "function", nullable = false, updatable = false)
    public String function;

    @Column(name = "view_id", updatable = false)
    public @Nullable UUID viewId;

    @Column(name = "element_archi_id", updatable = false)
    public @Nullable String elementArchiId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    public String idempotencyKey;

    @Column(name = "fingerprint", nullable = false, updatable = false)
    public String fingerprint;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "context_tokens")
    public @Nullable Integer contextTokens;

    @Column(name = "context_truncated", nullable = false)
    public boolean contextTruncated;

    @Column(name = "prompt_tokens", nullable = false)
    public long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    public long completionTokens;

    @Column(name = "rejection_reason")
    public @Nullable String rejectionReason;

    @Column(name = "result")
    public @Nullable String result;

    @Column(name = "requested_by", nullable = false, updatable = false)
    public String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    public Instant requestedAt;

    @Column(name = "first_token_at")
    public @Nullable Instant firstTokenAt;

    @Column(name = "finished_at")
    public @Nullable Instant finishedAt;
}
