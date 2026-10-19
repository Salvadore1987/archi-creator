package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;

class ModelStatusTest {

    @Test
    @DisplayName("INV-MDL-002: удалённую модель не правят и не выгружают — каждое правило со своим кодом")
    void deletedModelIsNeitherEditedNorExported() {
        assertDoesNotThrow(ModelStatus.ACTIVE::requireActive);
        assertDoesNotThrow(ModelStatus.ACTIVE::requireExportable);

        assertEquals(ModelingCodes.LIFECYCLE,
                assertThrows(ModelingDomainException.class, ModelStatus.DELETED::requireActive).code());
        assertEquals(ModelingCodes.MODEL_DELETED,
                assertThrows(ModelingDomainException.class, ModelStatus.DELETED::requireExportable).code());
    }
}
