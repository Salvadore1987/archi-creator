package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.Collection;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;

/**
 * Публикация доменных событий после коммита (ADR-0003, {@code direct}). Вне транзакции
 * события уходят сразу; откат транзакции их не публикует.
 */
public interface DomainEventPublisher {

    void publish(Collection<? extends DomainEvent> events);
}
