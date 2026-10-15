package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Строка сессии помощника: параметры обращения, состояние, расход и результат в столбце {@code result}. */
@Entity
@Table(name = "advisor_session")
public class AdvisorSessionEntity {

    @Id
    public UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    public UUID workspaceId;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(nullable = false, updatable = false)
    public String function;

    @Column(name = "view_id", updatable = false)
    public UUID viewId;

    @Column(name = "element_archi_id", updatable = false)
    public String elementArchiId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    public String idempotencyKey;

    @Column(nullable = false, updatable = false)
    public String fingerprint;

    @Column(nullable = false)
    public String status;

    @Column(name = "context_tokens")
    public Integer contextTokens;

    @Column(name = "context_truncated", nullable = false)
    public boolean contextTruncated;

    @Column(name = "prompt_tokens", nullable = false)
    public long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    public long completionTokens;

    @Column(name = "rejection_reason")
    public String rejectionReason;

    public String result;

    @Column(name = "requested_by", nullable = false, updatable = false)
    public String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    public Instant requestedAt;

    @Column(name = "first_token_at")
    public Instant firstTokenAt;

    @Column(name = "finished_at")
    public Instant finishedAt;
}
