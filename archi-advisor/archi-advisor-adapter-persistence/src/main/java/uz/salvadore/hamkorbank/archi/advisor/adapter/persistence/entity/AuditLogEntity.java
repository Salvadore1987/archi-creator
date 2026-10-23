package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Запись аудита обращения — неизменяема после вставки. */
@Entity
@Table(name = "ai_audit_log")
public class AuditLogEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    public UUID workspaceId;

    @Column(name = "model_id", updatable = false)
    public @Nullable UUID modelId;

    @Column(name = "session_id", updatable = false)
    public @Nullable UUID sessionId;

    @Column(name = "user_subject", nullable = false, updatable = false)
    public String userSubject;

    @Column(name = "action", nullable = false, updatable = false)
    public String action;

    @Column(name = "outcome", nullable = false, updatable = false)
    public String outcome;

    @Column(name = "rejection_reason", updatable = false)
    public @Nullable String rejectionReason;

    @Column(name = "prompt_tokens", nullable = false, updatable = false)
    public int promptTokens;

    @Column(name = "completion_tokens", nullable = false, updatable = false)
    public int completionTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
