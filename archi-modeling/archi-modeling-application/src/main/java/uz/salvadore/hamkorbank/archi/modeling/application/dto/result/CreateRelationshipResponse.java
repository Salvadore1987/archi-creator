package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Итог создания связи.
 *
 * @param created ложно у возвращённого дубля того же типа между той же парой
 * @param edgeId  ребро на представлении; {@code null} — ребро не просили
 */
public record CreateRelationshipResponse(RelationshipResponse relationship, boolean created, @Nullable UUID edgeId) {
}
