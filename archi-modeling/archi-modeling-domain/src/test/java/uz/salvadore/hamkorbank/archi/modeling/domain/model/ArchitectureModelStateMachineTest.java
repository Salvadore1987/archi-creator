package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.ARCHITECT;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelDeleted;

class ArchitectureModelStateMachineTest {

    @Test
    @DisplayName("INV-MDL-002: переходы только ACTIVE ⇄ DELETED → PURGED")
    void onlyAllowedTransitionsArePermitted() {
        ArchitectureModel model = Models.empty();

        ModelingException directPurge = assertThrows(ModelingException.class, () -> model.purge(NOW));
        assertEquals("INV-MDL-002", directPurge.code());
        assertEquals(ModelStatus.ACTIVE, model.status());

        model.delete(ARCHITECT, NOW);
        assertEquals(ModelStatus.DELETED, model.status());
        assertEquals(ModelDeleted.class, model.pullEvents().getFirst().getClass());

        model.restoreDeleted(NOW);
        assertEquals(ModelStatus.ACTIVE, model.status());

        model.delete(ARCHITECT, NOW);
        model.purge(NOW);
        assertEquals(ModelStatus.PURGED, model.status());
        assertThrows(ModelingException.class, () -> model.restoreDeleted(NOW), "PURGED терминален");
    }

    @Test
    @DisplayName("INV-MDL-002: в DELETED изменение содержимого отклоняется с 409")
    void deletedModelRejectsContentChanges() {
        ArchitectureModel model = Models.empty();
        model.delete(ARCHITECT, NOW);

        ModelingException rejected = assertThrows(ModelingException.class,
                () -> Models.element(model, "BusinessActor", FolderType.BUSINESS, "Клиент"));

        assertEquals("INV-MDL-002", rejected.code());
        assertEquals(Failure.CONFLICT, rejected.failure());
    }
}
