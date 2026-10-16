package uz.salvadore.hamkorbank.archi.modeling.domain.event;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;

/** Событие блокировки модели: закрытый перечень того, что с ним происходит. */
public sealed interface ModelLockEvent extends DomainEvent permits ModelLockReleasedEvent {
}
