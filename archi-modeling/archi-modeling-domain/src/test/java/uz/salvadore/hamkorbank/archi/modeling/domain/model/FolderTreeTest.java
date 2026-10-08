package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

class FolderTreeTest {

    @Test
    @DisplayName("INV-MDL-009: девять корней с момента создания, неудаляемы")
    void rootFoldersAlwaysExist() {
        ArchitectureModel model = Models.empty();

        assertEquals(List.of(FolderType.values()), List.copyOf(model.roots().keySet()));
        assertEquals("Technology & Physical", model.roots().get(FolderType.TECHNOLOGY).name());
        ModelingException rootDeletion = assertThrows(ModelingException.class,
                () -> model.removeFolder(Models.root(model, FolderType.OTHER), NOW));
        assertEquals("INV-MDL-009", rootDeletion.code());
    }

    @Test
    @DisplayName("INV-MDL-009: импорт без корня получает его дописанным, как в Archi")
    void importedModelGetsMissingRoots() {
        ModelHeader header = new ModelHeader(ModelId.next(UUIDS), WorkspaceId.next(UUIDS), Models.archiId(), "Файл",
                Optional.empty(), "5.0.0", ModelStatus.ACTIVE, List.of(), Optional.empty(), "a", NOW, NOW, 0);
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

        ModelingException cycle = assertThrows(ModelingException.class,
                () -> model.moveToFolder(core.id(), List.of(masters.id().value()), Instant.now()));

        assertEquals("INV-MDL-009", cycle.code());
        assertEquals(Optional.of(application), model.folders().get(masters.id()).orElseThrow().parentId());
    }

    @Test
    @DisplayName("INV-MDL-009: перенос не уводит объект из поддерева своего корня")
    void moveAcrossRootsIsRejected() {
        ArchitectureModel model = Models.empty();
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");

        ModelingException rejected = assertThrows(ModelingException.class, () -> model.moveToFolder(
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

        ModelingException notEmpty = assertThrows(ModelingException.class,
                () -> model.removeFolder(masters.id(), NOW));

        assertEquals(ModelingException.Codes.FOLDER_NOT_EMPTY, notEmpty.code());
    }
}
