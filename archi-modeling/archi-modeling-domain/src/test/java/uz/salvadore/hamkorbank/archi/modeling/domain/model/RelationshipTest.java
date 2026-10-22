package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;

class RelationshipTest {

    private static final ArchiType ASSOCIATION = ArchiType.ofSimpleName("AssociationRelationship");

    @Test
    @DisplayName("FR-03: opaque-связь без остатка XML не существует — её нечем было бы выгрузить")
    void opaqueRelationshipRequiresResidue() {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new Relationship(RelationshipId.next(UUIDS), FolderId.next(UUIDS), Models.archiId(),
                        ASSOCIATION, ElementId.next(UUIDS), ElementId.next(UUIDS), null, null, null, null,
                        List.of(), SortOrder.ofPosition(0), false, null));

        assertEquals(ModelingMessages.OPAQUE_WITHOUT_RESIDUE, rejected.reason().key());
    }

    @Test
    @DisplayName("INV-MDL-004: связь не бывает концом самой себя")
    void relationshipCannotEndAtItself() {
        RelationshipId id = RelationshipId.next(UUIDS);

        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new Relationship(id, FolderId.next(UUIDS), Models.archiId(), ASSOCIATION,
                        ElementId.next(UUIDS), id, null, null, null, null,
                        List.of(), SortOrder.ofPosition(0), true, new RawXml("<element/>")));

        assertEquals(ModelingMessages.RELATIONSHIP_SELF_END, rejected.reason().key());
    }
}
