package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Collection;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;

/** Публикация доменных событий помощника — после коммита, в процессе. */
public interface AdvisorEvents {

    /** Отдать события подписчикам; вызывается после коммита транзакции. */
    void publish(Collection<DomainEvent> events);
}
