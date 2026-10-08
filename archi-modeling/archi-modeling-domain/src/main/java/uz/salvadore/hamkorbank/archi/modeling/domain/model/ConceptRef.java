package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.UUID;

/**
 * Конец связи — элемент или связь той же модели (INV-MDL-004). Связь концом связи —
 * законный ArchiMate: ассоциация к связи, агрегация связи.
 */
public sealed interface ConceptRef permits ElementId, RelationshipId {

    UUID value();
}
