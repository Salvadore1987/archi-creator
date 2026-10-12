package uz.salvadore.hamkorbank.archi.advisor.domain.budget;

import java.time.Instant;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Расход достиг порога предупреждения. Работа продолжается — событие только уведомляет. */
public record TokenBudgetThresholdReached(WorkspaceId workspaceId, BudgetPeriod period, long limitTokens,
                                          long consumedTokens, int thresholdPercent, Instant occurredAt)
        implements DomainEvent {
}
