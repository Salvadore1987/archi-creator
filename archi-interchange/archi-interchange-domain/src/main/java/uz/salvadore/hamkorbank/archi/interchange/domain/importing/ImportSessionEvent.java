package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.DomainEvent;

/** Событие сессии импорта: закрытый перечень того, что с ним происходит. */
public sealed interface ImportSessionEvent extends DomainEvent permits ImportSessionAppliedEvent {
}
