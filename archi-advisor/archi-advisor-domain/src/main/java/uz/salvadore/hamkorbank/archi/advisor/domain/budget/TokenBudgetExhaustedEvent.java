package uz.salvadore.hamkorbank.archi.advisor.domain.budget;

import java.time.Instant;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Месячный лимит исчерпан: новые обращения отклоняются до смены периода или повышения лимита. */
public record TokenBudgetExhaustedEvent(WorkspaceId workspaceId, BudgetPeriod period, long limitTokens,
                                   long consumedTokens, Instant occurredAt) implements TokenBudgetEvent {
}
