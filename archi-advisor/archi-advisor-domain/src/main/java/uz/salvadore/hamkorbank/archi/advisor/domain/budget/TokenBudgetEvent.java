package uz.salvadore.hamkorbank.archi.advisor.domain.budget;

import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;

/** Событие бюджета токенов: закрытый перечень того, что с ним происходит. */
public sealed interface TokenBudgetEvent extends DomainEvent
        permits TokenBudgetExhaustedEvent, TokenBudgetThresholdReachedEvent {
}
