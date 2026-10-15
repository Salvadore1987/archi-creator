package uz.salvadore.hamkorbank.archi.interchange.domain.common;

import java.time.Instant;

/**
 * Доменное событие interchange. Публикует слой приложения после коммита транзакции,
 * в том же процессе.
 */
public interface DomainEvent {

    /** Когда произошло событие. */
    Instant occurredAt();
}
