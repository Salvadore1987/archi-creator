package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Связи модели: создать по матрице, изменить, удалить. */
public interface RelationshipApplicationService {

    /** Новая связь или дубль того же типа между той же парой; по просьбе — с ребром. */
    CreateRelationshipResponse createRelationship(EditorIdentity actor, CreateRelationshipCommand command);

    /** Имя, описание и свойства; незаданное поле не меняется. */
    RelationshipResponse editRelationship(EditorIdentity actor, EditRelationshipCommand command);

    /** Удаление вместе с рёбрами; примыкающая связь — {@code 409}. */
    void deleteRelationship(EditorIdentity actor, DeleteRelationshipCommand command);
}
