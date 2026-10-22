package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class ViewEdgeTest {

    @Test
    @DisplayName("INV-MDL-008: ребро не бывает концом самого себя")
    void edgeCannotEndAtItself() {
        ViewEdgeId id = ViewEdgeId.next(UUIDS);

        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new ViewEdge(id, Models.archiId(), DiagramType.CONNECTION, null, ViewNodeId.next(UUIDS), id,
                        List.of(), StyleOverride.NONE, SortOrder.ofPosition(0), null));

        assertEquals(ModelingMessages.EDGE_SELF_END, rejected.reason().key());
    }
}
