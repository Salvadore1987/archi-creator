package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

class FolderTreeTest {

    @Test
    @DisplayName("INV-MDL-009: девять корней с момента создания, неудаляемы")
    void rootFoldersAlwaysExist() {
        ArchitectureModel model = Models.empty();

        assertEquals(List.of(FolderType.values()), List.copyOf(model.roots().keySet()));
        assertEquals("Technology & Physical", model.roots().get(FolderType.TECHNOLOGY).name());
        ModelingDomainException rootDeletion = assertThrows(ModelingDomainException.class,
                () -> model.removeFolder(Models.root(model, FolderType.OTHER), NOW));
        assertEquals("INV-MDL-009", rootDeletion.code());
    }

    @Test
    @DisplayName("INV-MDL-009: импорт без корня получает его дописанным, как в Archi")
    void importedModelGetsMissingRoots() {
        ModelHeader header = new ModelHeader(ModelId.next(UUIDS), WorkspaceId.next(UUIDS), Models.archiId(), "Файл",
                null, "5.0.0", ModelStatus.ACTIVE, List.of(), null, "a", NOW, NOW, 0);
        ModelFolder business = FolderTreeInitializer.root(FolderType.BUSINESS, FolderId.next(UUIDS),
                Models.archiId(), SortOrder.ofPosition(0));

        ArchitectureModel model = ArchitectureModel.imported(header, List.of(business), List.of(), List.of(),
                List.of(), () -> FolderId.next(UUIDS), Models::archiId);

        assertEquals(9, model.roots().size());
        assertEquals(business.id(), model.roots().get(FolderType.BUSINESS).id());
    }

    @Test
    @DisplayName("INV-MDL-009: папка не переносится внутрь своего потомка")
    void moveIntoOwnSubtreeIsRejected() {
        ArchitectureModel model = Models.empty();
        FolderId application = Models.root(model, FolderType.APPLICATION);
        ModelFolder masters = model.createFolder(application, "Мастер-системы", FolderId.next(UUIDS),
                Models.archiId(), NOW);
        ModelFolder core = model.createFolder(masters.id(), "Ядро", FolderId.next(UUIDS), Models.archiId(), NOW);

        ModelingDomainException cycle = assertThrows(ModelingDomainException.class,
                () -> model.moveToFolder(core.id(), List.of(masters.id().value()), NOW));

        assertEquals("INV-MDL-009", cycle.code());
        assertEquals(application, model.folders().get(masters.id()).orElseThrow().parentId());
    }

    @Test
    @DisplayName("INV-MDL-009: перенос не уводит объект из поддерева своего корня")
    void moveAcrossRootsIsRejected() {
        ArchitectureModel model = Models.empty();
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class, () -> model.moveToFolder(
                Models.root(model, FolderType.APPLICATION), List.of(actor.id().value()), NOW));

        assertEquals("INV-MDL-009", rejected.code());
    }

    @Test
    @DisplayName("UC-MDL-006: непустая пользовательская папка не удаляется")
    void nonEmptyFolderIsNotRemoved() {
        ArchitectureModel model = Models.empty();
        ModelFolder masters = model.createFolder(Models.root(model, FolderType.APPLICATION), "Мастер-системы",
                FolderId.next(UUIDS), Models.archiId(), NOW);
        model.moveToFolder(masters.id(),
                List.of(Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС").id().value()),
                NOW);

        ModelingDomainException notEmpty = assertThrows(ModelingDomainException.class,
                () -> model.removeFolder(masters.id(), NOW));

        assertEquals(ModelingCodes.FOLDER_NOT_EMPTY, notEmpty.code());
    }

    @Test
    @DisplayName("UC-MDL-006: нарушение у одного объекта — перенос не меняет ни одного")
    void rejectedBatchChangesNothing() {
        ArchitectureModel model = Models.empty();
        FolderId application = Models.root(model, FolderType.APPLICATION);
        ModelFolder target = model.createFolder(application, "Мастер-системы", FolderId.next(UUIDS),
                Models.archiId(), NOW);
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");
        model.markPersisted();

        assertThrows(ModelingDomainException.class, () -> model.moveToFolder(target.id(),
                List.of(component.id().value(), actor.id().value()), NOW));

        assertEquals(application, model.elements().get(component.id()).orElseThrow().folderId());
        assertFalse(model.elements().changed(), "отклонённый перенос ничего не оставил для записи");
    }

    @Test
    @DisplayName("INV-MDL-009: импорт с двумя корнями одного вида отклоняется")
    void importWithRepeatedRootIsRejected() {
        List<ModelFolder> roots = List.of(
                FolderTreeInitializer.root(FolderType.BUSINESS, FolderId.next(UUIDS), Models.archiId(),
                        SortOrder.ofPosition(0)),
                FolderTreeInitializer.root(FolderType.BUSINESS, FolderId.next(UUIDS), Models.archiId(),
                        SortOrder.ofPosition(1)));

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> ArchitectureModel.imported(header(), roots, List.of(), List.of(), List.of(),
                        () -> FolderId.next(UUIDS), Models::archiId));

        assertEquals(ModelingCodes.FOLDER_TREE, rejected.code());
        assertEquals(ModelingMessages.ROOT_FOLDER_REPEATED, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-009: импорт с папками, вложенными друг в друга по кругу, отклоняется")
    void importWithFolderCycleIsRejected() {
        FolderId first = FolderId.next(UUIDS);
        FolderId second = FolderId.next(UUIDS);
        List<ModelFolder> folders = List.of(
                new ModelFolder(first, second, Models.archiId(), "Первая", null, SortOrder.ofPosition(0), null),
                new ModelFolder(second, first, Models.archiId(), "Вторая", null, SortOrder.ofPosition(0), null));

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> ArchitectureModel.imported(header(), folders, List.of(), List.of(), List.of(),
                        () -> FolderId.next(UUIDS), Models::archiId));

        assertEquals(ModelingCodes.FOLDER_TREE, rejected.code());
        assertEquals(ModelingMessages.FOLDER_CYCLE, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-009: корневая папка не переносится")
    void rootFolderIsNotMovable() {
        ArchitectureModel model = Models.empty();
        FolderId business = Models.root(model, FolderType.BUSINESS);
        ModelFolder target = model.createFolder(business, "Клиенты", FolderId.next(UUIDS), Models.archiId(), NOW);

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> model.moveToFolder(target.id(), List.of(business.value()), NOW));

        assertEquals(ModelingCodes.FOLDER_TREE, rejected.code());
        assertEquals(ModelingMessages.ROOT_FOLDER_UNMOVABLE, rejected.reason().key());
        assertNull(model.folders().get(business).orElseThrow().parentId());
    }

    @Test
    @DisplayName("UC-MDL-006: перенос объекта, которого нет в модели, — «не найдено»")
    void movingUnknownObjectIsNotFound() {
        ArchitectureModel model = Models.empty();

        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> model.moveToFolder(Models.root(model, FolderType.BUSINESS), List.of(UUIDS.next()), NOW));

        assertEquals(ModelingCodes.NOT_FOUND, rejected.code());
        assertEquals(Failure.NOT_FOUND, rejected.failure());
    }

    private static ModelHeader header() {
        return new ModelHeader(ModelId.next(UUIDS), WorkspaceId.next(UUIDS), Models.archiId(), "Файл",
                null, "5.0.0", ModelStatus.ACTIVE, List.of(), null, "a", NOW, NOW, 0);
    }
}
