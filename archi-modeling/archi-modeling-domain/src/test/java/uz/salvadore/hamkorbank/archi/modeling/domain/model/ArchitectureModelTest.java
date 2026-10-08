package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationNotPermittedException;

class ArchitectureModelTest {

    @Test
    @DisplayName("INV-MDL-001: archi_id уникален в пределах модели")
    void archiIdIsUniqueWithinModel() {
        ArchitectureModel model = Models.empty();
        Element first = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");

        ModelingException taken = assertThrows(ModelingException.class, () -> model.addElement(
                ArchiType.ofSimpleName("ApplicationComponent"), "Двойник", Models.root(model, FolderType.APPLICATION),
                ElementId.next(UUIDS), first.archiId(), NOW));

        assertEquals("INV-MDL-001", taken.code());
        assertEquals(1, model.elements().size());
        assertTrue(model.archiIdTaken(model.roots().get(FolderType.DIAGRAMS).archiId()),
                "папки — тоже объекты с archi_id");
    }

    @Test
    @DisplayName("UC-MDL-002: элемент встаёт в конец папки своего слоя")
    void elementLandsAtEndOfItsLayerFolder() {
        ArchitectureModel model = Models.empty();
        Element first = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");
        Element second = Models.element(model, "BusinessRole", FolderType.BUSINESS, "Плательщик");

        assertEquals(1000, first.sortOrder().value());
        assertEquals(2000, second.sortOrder().value());
        assertTrue(model.elements().upserts().containsKey(second.id()), "новое попадает в изменения для хранилища");
    }

    @Test
    @DisplayName("INV-MDL-009: элемент слоя Business в папку Application не кладётся")
    void elementOutsideItsLayerFolderIsRejected() {
        ArchitectureModel model = Models.empty();

        ModelingException wrongFolder = assertThrows(ModelingException.class,
                () -> Models.element(model, "BusinessActor", FolderType.APPLICATION, "Клиент"));

        assertEquals("INV-MDL-009", wrongFolder.code());
    }

    @Test
    @DisplayName("FR-07: тип фазы 2 создать нельзя — он opaque до своей фазы")
    void phaseTwoTypeIsNotCreatable() {
        ArchitectureModel model = Models.empty();

        ModelingException phase2 = assertThrows(ModelingException.class,
                () -> Models.element(model, "Capability", FolderType.STRATEGY, "Платежи"));

        assertEquals(ModelingException.Codes.TYPE_NOT_EDITABLE, phase2.code());
        assertEquals(Failure.UNPROCESSABLE, phase2.failure());
    }

    @Test
    @DisplayName("INV-MDL-007: связь по матрице, дубль возвращает существующую")
    void relationshipIsCheckedByMatrixAndNotDuplicated() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");

        Relationship serving = Models.relate(model, "ServingRelationship", component.id(), actor.id());
        ArchitectureModel.Created<Relationship> again = model.addRelationship(
                ArchiType.ofSimpleName("ServingRelationship"), component.id(), actor.id(), Optional.empty(),
                RelationshipId.next(UUIDS), Models.archiId(), NOW);

        assertFalse(again.created());
        assertEquals(serving.id(), again.value().id());
        assertEquals(Models.root(model, FolderType.RELATIONS), serving.folderId());
        assertThrows(RelationNotPermittedException.class,
                () -> Models.relate(model, "AssignmentRelationship", component.id(), actor.id()));
    }

    @Test
    @DisplayName("INV-MDL-004: ассоциация к связи — законный конец")
    void relationshipMayEndAtRelationship() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "Антифрод");
        Element service = Models.element(model, "ApplicationService", FolderType.APPLICATION, "Платежи");
        Element process = Models.element(model, "BusinessProcess", FolderType.BUSINESS, "Платёж");
        Relationship serving = Models.relate(model, "ServingRelationship", service.id(), process.id());

        Relationship association = Models.relate(model, "AssociationRelationship", component.id(), serving.id());

        assertEquals(serving.id(), association.target());
        ModelingException blocked = assertThrows(ModelingException.class,
                () -> model.removeRelationship(serving.id(), NOW));
        assertEquals("INV-MDL-004", blocked.code());
    }
}
