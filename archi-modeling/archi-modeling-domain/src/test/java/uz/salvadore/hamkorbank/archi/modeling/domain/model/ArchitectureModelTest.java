package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationNotPermittedException;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

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
    @DisplayName("FR-08: тип фазы 3 создать нельзя — он opaque до своей фазы")
    void phaseThreeTypeIsNotCreatable() {
        ArchitectureModel model = Models.empty();

        ModelingException phase3 = assertThrows(ModelingException.class,
                () -> Models.element(model, "WorkPackage", FolderType.IMPLEMENTATION_MIGRATION, "Внедрение СМП"));

        assertEquals(ModelingCodes.TYPE_NOT_EDITABLE, phase3.code());
        assertEquals(Failure.UNPROCESSABLE, phase3.failure());
    }

    @Test
    @DisplayName("FR-08: Capability создаётся в папке Strategy, правится и связывается по матрице")
    void capabilityIsEditable() {
        ArchitectureModel model = Models.empty();
        Element capability = Models.element(model, "Capability", FolderType.STRATEGY, "Платежи");
        Element goal = Models.element(model, "Goal", FolderType.MOTIVATION, "Платёж за 10 секунд");
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");

        Element renamed = model.updateElement(capability.id(), "Мгновенные платежи", Optional.of("Уровень 1"),
                List.of(), NOW);
        Relationship realizes = Models.relate(model, "RealizationRelationship", component.id(), capability.id());
        Models.relate(model, "RealizationRelationship", capability.id(), goal.id());

        assertTrue(capability.supported());
        assertEquals("Мгновенные платежи", renamed.name());
        assertEquals(capability.id(), realizes.target());
        assertThrows(RelationNotPermittedException.class,
                () -> Models.relate(model, "AssignmentRelationship", capability.id(), component.id()));
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

    @Test
    @DisplayName("Переименование представления — правила модели: длина имени, активная модель")
    void renamingViewFollowsModelRules() {
        ArchitectureModel model = Models.empty();
        ViewRef view = model.placeNewView(ViewId.next(UUIDS), DiagramType.DIAGRAM_MODEL, "Контекст",
                Models.root(model, FolderType.DIAGRAMS), Models.archiId(), NOW);

        assertThrows(ModelingException.class, () -> model.renameView(view.id(), "x".repeat(Names.MAX + 1), NOW));
        assertThrows(ModelingException.class, () -> new ViewRef(view.id(), view.folderId(), view.archiId(),
                view.archiType(), "x".repeat(Names.MAX + 1), view.sortOrder()));

        model.renameView(view.id(), "Ландшафт", NOW);
        assertEquals("Ландшафт", model.view(view.id()).orElseThrow().name());

        model.delete(Models.ARCHITECT, NOW);
        assertThrows(ModelingException.class, () -> model.renameView(view.id(), "Другое", NOW));
    }
}
