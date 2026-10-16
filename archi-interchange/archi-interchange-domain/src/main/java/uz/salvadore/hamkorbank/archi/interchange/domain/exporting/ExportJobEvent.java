package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.DomainEvent;

/** Событие задания выгрузки: закрытый перечень того, что с ним происходит. */
public sealed interface ExportJobEvent extends DomainEvent permits ExportCompletedEvent {
}
