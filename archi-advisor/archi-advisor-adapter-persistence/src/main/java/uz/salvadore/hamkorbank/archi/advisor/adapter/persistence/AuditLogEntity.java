package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Запись аудита обращения — неизменяема после вставки. */
@Entity
@Table(name = "ai_audit_log")
public class AuditLogEntity {

    @Id
    public UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    public UUID workspaceId;

    @Column(name = "model_id", updatable = false)
    public UUID modelId;

    @Column(name = "session_id", updatable = false)
    public UUID sessionId;

    @Column(name = "user_subject", nullable = false, updatable = false)
    public String userSubject;

    @Column(nullable = false, updatable = false)
    public String action;

    @Column(nullable = false, updatable = false)
    public String outcome;

    @Column(name = "rejection_reason", updatable = false)
    public String rejectionReason;

    @Column(name = "prompt_tokens", nullable = false, updatable = false)
    public int promptTokens;

    @Column(name = "completion_tokens", nullable = false, updatable = false)
    public int completionTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
