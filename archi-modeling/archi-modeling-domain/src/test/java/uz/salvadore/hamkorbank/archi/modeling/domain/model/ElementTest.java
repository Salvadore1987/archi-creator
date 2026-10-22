package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;

class ElementTest {

    @Test
    @DisplayName("FR-03: opaque-элемент без остатка XML не существует — его нечем было бы выгрузить")
    void opaqueElementRequiresResidue() {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new Element(ElementId.next(UUIDS), FolderId.next(UUIDS), Models.archiId(),
                        ArchiType.ofSimpleName("Location"), "Ташкент", null, List.of(), SortOrder.ofPosition(0),
                        false, null));

        assertEquals(ModelingMessages.OPAQUE_WITHOUT_RESIDUE, rejected.reason().key());
    }
}
