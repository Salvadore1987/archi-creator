package uz.salvadore.hamkorbank.archi.modeling.domain.event;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;

/** Событие агрегата модели: закрытый перечень того, что с ним происходит. */
public sealed interface ArchitectureModelEvent extends DomainEvent permits ModelDeletedEvent {
}
