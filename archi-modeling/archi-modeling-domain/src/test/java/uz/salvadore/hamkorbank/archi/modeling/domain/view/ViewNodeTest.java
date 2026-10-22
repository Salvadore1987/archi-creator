package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class ViewNodeTest {

    @Test
    @DisplayName("INV-MDL-008: узел не вложен сам в себя")
    void nodeCannotBeItsOwnParent() {
        ViewNodeId id = ViewNodeId.next(UUIDS);

        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new ViewNode(id, id, Models.archiId(), DiagramType.GROUP, null, new Bounds(0, 0, 400, 140),
                        StyleOverride.NONE, SortOrder.ofPosition(0), null));

        assertEquals(ModelingMessages.NODE_SELF_PARENT, rejected.reason().key());
    }
}
