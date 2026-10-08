package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.time.Instant;

/**
 * Доменное событие modeling (spec/domain/modeling/events.yaml). Публикует слой
 * приложения после коммита транзакции (ADR-0003, {@code direct}).
 */
public interface DomainEvent {

    Instant occurredAt();
}
