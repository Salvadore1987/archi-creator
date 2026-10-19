package uz.salvadore.hamkorbank.archi.advisor.domain.budget;

import java.time.Instant;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Расход достиг порога предупреждения. Работа продолжается — событие только уведомляет. */
public record TokenBudgetThresholdReachedEvent(WorkspaceId workspaceId, BudgetPeriod period, long limitTokens,
                                          long consumedTokens, int thresholdPercent, Instant occurredAt)
        implements TokenBudgetEvent {

    public TokenBudgetThresholdReachedEvent {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
