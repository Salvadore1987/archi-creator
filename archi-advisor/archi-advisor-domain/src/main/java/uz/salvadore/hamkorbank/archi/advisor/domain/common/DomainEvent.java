package uz.salvadore.hamkorbank.archi.advisor.domain.common;

import java.time.Instant;

/** Доменное событие помощника: публикуется после коммита, в процессе. */
public interface DomainEvent {

    /** Момент, когда событие произошло, по часам приложения. */
    Instant occurredAt();
}
