package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.util.Collection;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.DomainEvent;

/** События interchange ({@code ImportApplied}, {@code ExportCompleted}) — после коммита. */
public interface InterchangeEvents {

    void publish(Collection<DomainEvent> events);
}
