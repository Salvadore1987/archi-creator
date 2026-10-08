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
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;

class RelationshipIntegrityTest {

    @Test
    @DisplayName("INV-MDL-004: элемент со связями не удаляется, каскада нет")
    void deletingConnectedElementIsRejected() {
        ArchitectureModel model = Models.empty();
        Element component = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element actor = Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент");
        Relationship serving = Models.relate(model, "ServingRelationship", component.id(), actor.id());

        ModelingException rejected = assertThrows(ModelingException.class,
                () -> model.removeElement(component.id(), NOW));

        assertEquals("INV-MDL-004", rejected.code());
        assertEquals(Failure.CONFLICT, rejected.failure());
        assertEquals(List.of(serving.id().toString()), rejected.details().get("relationships"));
        assertTrue(model.elements().contains(component.id()));

        model.removeRelationship(serving.id(), NOW);
        model.removeElement(component.id(), NOW);
        assertFalse(model.elements().contains(component.id()));
    }

    @Test
    @DisplayName("INV-MDL-004: конец связи из другой модели отклоняется")
    void foreignEndIsRejected() {
        ArchitectureModel model = Models.empty();
        ArchitectureModel other = Models.empty();
        Element own = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element foreign = Models.element(other, "BusinessActor", FolderType.BUSINESS, "Клиент");

        ModelingException rejected = assertThrows(ModelingException.class,
                () -> model.addRelationship(ArchiType.ofSimpleName("ServingRelationship"), own.id(), foreign.id(),
                        Optional.empty(), RelationshipId.next(UUIDS), Models.archiId(), NOW));

        assertEquals("INV-MDL-004", rejected.code());
        assertEquals(Failure.UNPROCESSABLE, rejected.failure());
    }
}
