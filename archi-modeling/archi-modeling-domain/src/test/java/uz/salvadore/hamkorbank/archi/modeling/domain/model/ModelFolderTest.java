package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;

class ModelFolderTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("INV-MDL-009: у папки либо вид корня, либо родитель — не оба сразу и не ни одного")
    void folderIsEitherRootOrNested(boolean both) {
        FolderType type = both ? FolderType.BUSINESS : null;
        FolderId parent = both ? FolderId.next(UUIDS) : null;

        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new ModelFolder(FolderId.next(UUIDS), parent, Models.archiId(), "Клиенты", type,
                        SortOrder.ofPosition(0), null));

        assertEquals(ModelingMessages.FOLDER_TYPE_OR_PARENT, rejected.reason().key());
    }
}
