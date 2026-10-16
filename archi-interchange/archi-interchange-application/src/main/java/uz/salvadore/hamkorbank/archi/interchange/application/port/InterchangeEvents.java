package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.util.Collection;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.DomainEvent;

/** События interchange ({@code ImportAppliedEvent}, {@code ExportCompletedEvent}) — после коммита. */
public interface InterchangeEvents {

    /** Отправляет события после коммита транзакции. */
    void publish(Collection<DomainEvent> events);
}
