package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_budget")
public class TokenBudgetEntity {

    @Id
    public UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    public UUID workspaceId;

    @Column(nullable = false, updatable = false)
    public String period;

    @Column(name = "consumed_tokens", nullable = false)
    public long consumedTokens;

    @Column(name = "warning_threshold_percent", nullable = false)
    public int warningThresholdPercent;

    @Column(nullable = false)
    public String status;

    @Column(nullable = false)
    public long version;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
