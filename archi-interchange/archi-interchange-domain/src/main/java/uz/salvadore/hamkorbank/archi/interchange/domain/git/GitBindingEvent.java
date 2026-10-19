package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.DomainEvent;

/** Событие привязки пространства к репозиторию Git: закрытый перечень того, что с ним происходит. */
public sealed interface GitBindingEvent extends DomainEvent permits GitBindingSnapshotCommittedEvent {
}
