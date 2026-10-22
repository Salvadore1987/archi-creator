package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationNotPermittedException;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

class ArchitectureModelTest {

    @Test
    @DisplayName("непрозрачные объекты модели — неподдержанные элементы и связи, поддержанные не в счёт")
    void opaqueCount() {
        RawXml residue = new RawXml("<element/>");
        FolderId other = FolderId.next(UUIDS);
        FolderId relations = FolderId.next(UUIDS);
        List<ModelFolder> roots = List.of(
                FolderTreeInitializer.root(FolderType.OTHER, other, Models.archiId(), SortOrder.ofPosition(0)),
                FolderTreeInitializer.root(FolderType.RELATIONS, relations, Models.archiId(), SortOrder.ofPosition(1)));
        Element location = new Element(ElementId.next(UUIDS), other, Models.archiId(),
                ArchiType.ofSimpleName("Location"), "Ташкент", null, List.of(), SortOrder.ofPosition(0), false,
                residue);
        Element grouping = new Element(ElementId.next(UUIDS), other, Models.archiId(),
                ArchiType.ofSimpleName("Grouping"), "Группа", null, List.of(), SortOrder.ofPosition(1), true, null);
        Relationship unknown = new Relationship(RelationshipId.next(UUIDS), relations, Models.archiId(),
                ArchiType.ofSimpleName("TeleportRelationship"), location.id(), grouping.id(), null,
                null, null, null, List.of(), SortOrder.ofPosition(0), false, residue);
        ModelHeader header = new ModelHeader(ModelId.next(UUIDS), WorkspaceId.next(UUIDS), Models.archiId(), "Файл",
                null, "5.0.0", ModelStatus.ACTIVE, List.of(), null, "a", NOW, NOW, 0);

        ArchitectureModel model = ArchitectureModel.imported(header, roots, List.of(location, grouping),
                List.of(unknown), List.of(), () -> FolderId.next(UUIDS), Models::archiId);

        assertEquals(2, model.opaqueCount());
    }

    @Test
    @DisplayName("в новой модели непрозрачных объектов нет")
    void emptyModelHasNoOpaqueObjects() {
        assertEquals(0, Models.empty().opaqueCount());
    }

    @Test
    @DisplayName("INV-MDL-001: archi_id уникален в пределах модели")
    void archiIdIsUniqueWithinModel() {
        ArchitectureModel model = Models.empty();
        Element first = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");

        ModelingDomainException taken = assertThrows(ModelingDomainException.class, () -> model.addElement(
                ArchiType.ofSimpleName("ApplicationComponent"), "Двойник", Models.root(model, FolderType.APPLICATION),
                ElementId.next(UUIDS), first.archiId(), NOW));

        assertEquals("INV-MDL-001", taken.code());
        assertEquals(1, model.elements().size());
    }

    @Test
    @DisplayName("INV-MDL-001: archi_id папки тоже занят в модели")
    void folderArchiIdIsTaken() {
        ArchitectureModel model = Models.empty();

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

        ModelingDomainException wrongFolder = assertThrows(ModelingDomainException.class,
                () -> Models.element(model, "BusinessActor", FolderType.APPLICATION, "Клиент"));

        assertEquals("INV-MDL-009", wrongFolder.code());
    }

    @Test
    @DisplayName("FR-08: тип фазы 3 создать нельзя — он opaque до своей фазы")
    void phaseThreeTypeIsNotCreatable() {
        ArchitectureModel model = Models.empty();

        ModelingDomainException phase3 = assertThrows(ModelingDomainException.class,
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

        Element renamed = model.updateElement(capability.id(), "Мгновенные платежи", "Уровень 1",
                List.of(), NOW);
        Relationship realizes = Models.relate(model, "RealizationRelationship", component.id(), capability.id());
        Models.relate(model, "RealizationRelationship", capability.id(), goal.id());

        assertTrue(capability.supported());
        assertEquals("Мгновенные платежи", renamed.name());
        assertEquals(capability.id(), realizes.target());
    }

    @Test
    @DisplayName("FR-08: связь Capability, которой нет в матрице, отклоняется")
    void capabilityRelationOutsideMatrixIsRejected() {
        ArchitectureModel model = Models.empty();
        Element capability = Models.element(model, "Capability", FolderType.STRATEGY, "Платежи");
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");

        assertThrows(RelationNotPermittedException.class,
                () -> Models.relate(model, "AssignmentRelationship", capability.id(), component.id()));
    }

    @Test
    @DisplayName("INV-MDL-007: дубль связи возвращает существующую")
    void duplicateRelationshipReturnsExisting() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");

        Relationship serving = Models.relate(model, "ServingRelationship", component.id(), actor.id());
        ArchitectureModel.Created<Relationship> again = model.addRelationship(
                ArchiType.ofSimpleName("ServingRelationship"), component.id(), actor.id(), null,
                RelationshipId.next(UUIDS), Models.archiId(), NOW);

        assertFalse(again.created());
        assertEquals(serving.id(), again.value().id());
        assertEquals(Models.root(model, FolderType.RELATIONS), serving.folderId());
    }

    @Test
    @DisplayName("INV-MDL-007: связь, которой нет в матрице, отклоняется")
    void relationshipOutsideMatrixIsRejected() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");

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
        ModelingDomainException blocked = assertThrows(ModelingDomainException.class,
                () -> model.removeRelationship(serving.id(), NOW));
        assertEquals("INV-MDL-004", blocked.code());
    }

    @Test
    @DisplayName("Представление переименовывается")
    void viewIsRenamed() {
        ArchitectureModel model = Models.empty();
        ViewRef view = contextView(model);

        model.renameView(view.id(), "Ландшафт", NOW);

        assertEquals("Ландшафт", model.view(view.id()).orElseThrow().name());
    }

    @Test
    @DisplayName("Имя представления длиннее предела при переименовании отклоняется")
    void tooLongViewNameIsRejectedOnRename() {
        ArchitectureModel model = Models.empty();
        ViewRef view = contextView(model);

        assertThrows(ModelingDomainException.class, () -> model.renameView(view.id(), "x".repeat(Names.MAX + 1), NOW));
    }

    @Test
    @DisplayName("Ссылка на представление с именем длиннее предела не создаётся")
    void tooLongViewNameIsRejectedInViewRef() {
        ArchitectureModel model = Models.empty();
        ViewRef view = contextView(model);

        assertThrows(ModelingDomainException.class, () -> new ViewRef(view.id(), view.folderId(), view.archiId(),
                view.archiType(), "x".repeat(Names.MAX + 1), view.sortOrder()));
    }

    @Test
    @DisplayName("Представление удалённой модели не переименовывается")
    void viewOfDeletedModelIsNotRenamed() {
        ArchitectureModel model = Models.empty();
        ViewRef view = contextView(model);

        model.delete(Models.ARCHITECT, NOW);

        assertThrows(ModelingDomainException.class, () -> model.renameView(view.id(), "Другое", NOW));
    }

    @Test
    @DisplayName("Имя нового элемента с символом вне XML отклоняется — выгрузка не ломается")
    void elementNameOutsideXmlIsRejected() {
        ArchitectureModel model = Models.empty();
        FolderId application = Models.root(model, FolderType.APPLICATION);

        assertThrows(ModelingDomainException.class, () -> model.addElement(
                ArchiType.ofSimpleName("ApplicationComponent"), "а\u0001б", application, ElementId.next(UUIDS),
                Models.archiId(), NOW));
    }

    @Test
    @DisplayName("Описание элемента с одиночным суррогатом отклоняется — выгрузка не ломается")
    void documentationOutsideXmlIsRejected() {
        ArchitectureModel model = Models.empty();
        Element element = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");

        assertThrows(ModelingDomainException.class, () -> model.updateElement(element.id(), "АБС", "текст\uD800",
                List.of(), NOW));
    }

    @Test
    @DisplayName("Значение свойства с символом вне XML отклоняется — выгрузка не ломается")
    void propertyValueOutsideXmlIsRejected() {
        ArchitectureModel model = Models.empty();
        Element element = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");

        assertThrows(ModelingDomainException.class, () -> model.updateElement(element.id(), "АБС", null,
                List.of(new PropertyEntry("ключ", "\uFFFE", SortOrder.ofPosition(0))), NOW));
    }

    @Test
    @DisplayName("Имя папки с управляющим символом отклоняется — выгрузка не ломается")
    void folderNameOutsideXmlIsRejected() {
        ArchitectureModel model = Models.empty();
        FolderId application = Models.root(model, FolderType.APPLICATION);

        assertThrows(ModelingDomainException.class, () -> model.createFolder(application, "\u0007",
                FolderId.next(UUIDS), Models.archiId(), NOW));
    }

    @Test
    @DisplayName("Табуляция и перевод строки — допустимый текст файла Archi, они сохраняются")
    void tabAndNewlineAreKept() {
        ArchitectureModel model = Models.empty();
        Element element = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");

        Element kept = model.updateElement(element.id(), "АБС\tтаб", "строка\nстрока", List.of(), NOW);

        assertEquals("АБС\tтаб", kept.name());
    }

    @Test
    @DisplayName("Вид доступа задаётся только у связи Access")
    void accessTypeOutsideAccessIsRejected() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class, () -> model.addRelationship(
                ArchiType.ofSimpleName("ServingRelationship"), component.id(), actor.id(), null, null,
                AccessType.READ, null, RelationshipId.next(UUIDS), Models.archiId(), NOW));

        assertEquals(ModelingCodes.INVALID_INPUT, rejected.code());
        assertEquals(ModelingMessages.ACCESS_TYPE_MISPLACED, rejected.reason().key());
        assertEquals(0, model.relationships().size());
    }

    @Test
    @DisplayName("Направленность задаётся только у связи Association")
    void directedOutsideAssociationIsRejected() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class, () -> model.addRelationship(
                ArchiType.ofSimpleName("ServingRelationship"), component.id(), actor.id(), null, null,
                null, Boolean.TRUE, RelationshipId.next(UUIDS), Models.archiId(), NOW));

        assertEquals(ModelingCodes.INVALID_INPUT, rejected.code());
        assertEquals(ModelingMessages.DIRECTED_MISPLACED, rejected.reason().key());
        assertEquals(0, model.relationships().size());
    }

    @Test
    @DisplayName("FR-08: opaque-элемент из файла хранится, но не правится")
    void opaqueElementIsNotEditable() {
        FolderId other = FolderId.next(UUIDS);
        Element location = new Element(ElementId.next(UUIDS), other, Models.archiId(),
                ArchiType.ofSimpleName("Location"), "Ташкент", null, List.of(), SortOrder.ofPosition(0), false,
                new RawXml("<element/>"));
        ModelHeader header = new ModelHeader(ModelId.next(UUIDS), WorkspaceId.next(UUIDS), Models.archiId(), "Файл",
                null, "5.0.0", ModelStatus.ACTIVE, List.of(), null, "a", NOW, NOW, 0);
        ArchitectureModel model = ArchitectureModel.imported(header,
                List.of(FolderTreeInitializer.root(FolderType.OTHER, other, Models.archiId(), SortOrder.ofPosition(0))),
                List.of(location), List.of(), List.of(), () -> FolderId.next(UUIDS), Models::archiId);

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> model.updateElement(location.id(), "Самарканд", null, List.of(), NOW));

        assertEquals(ModelingCodes.TYPE_NOT_EDITABLE, rejected.code());
        assertEquals(ModelingMessages.OPAQUE_NOT_EDITABLE, rejected.reason().key());
        assertEquals("Ташкент", model.elements().get(location.id()).orElseThrow().name());
    }

    private static ViewRef contextView(ArchitectureModel model) {
        return model.placeNewView(ViewId.next(UUIDS), DiagramType.DIAGRAM_MODEL, "Контекст",
                Models.root(model, FolderType.DIAGRAMS), Models.archiId(), NOW);
    }
}
