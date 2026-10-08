package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.time.Instant;

/**
 * Доменное событие modeling. Публикует слой приложения после коммита транзакции,
 * в том же процессе.
 */
public interface DomainEvent {

    Instant occurredAt();
}
