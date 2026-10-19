package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Удалить связь вместе с её рёбрами. */
public record DeleteRelationshipCommand(UUID relationshipId) {
}
