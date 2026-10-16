package uz.salvadore.hamkorbank.archi.modeling.domain.event;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;

/** Событие версии модели: закрытый перечень того, что с ним происходит. */
public sealed interface ModelVersionEvent extends DomainEvent permits ModelVersionCommittedEvent {
}
