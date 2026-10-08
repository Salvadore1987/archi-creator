package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.util.Collection;

/** События interchange ({@code ImportApplied}, {@code ExportCompleted}) — после коммита. */
public interface InterchangeEvents {

    void publish(Collection<?> events);
}
