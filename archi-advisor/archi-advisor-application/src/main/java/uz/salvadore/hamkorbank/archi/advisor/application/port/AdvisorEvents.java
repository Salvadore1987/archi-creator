package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Collection;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.DomainEvent;

/** Публикация доменных событий помощника — после коммита, в процессе. */
public interface AdvisorEvents {

    void publish(Collection<? extends DomainEvent> events);
}
