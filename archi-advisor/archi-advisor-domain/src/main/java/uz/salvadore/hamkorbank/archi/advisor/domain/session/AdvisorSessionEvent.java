package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;

/** Событие сессии помощника: закрытый перечень того, что с ним происходит. */
public sealed interface AdvisorSessionEvent extends DomainEvent permits AdvisorSessionCompletedEvent {
}
