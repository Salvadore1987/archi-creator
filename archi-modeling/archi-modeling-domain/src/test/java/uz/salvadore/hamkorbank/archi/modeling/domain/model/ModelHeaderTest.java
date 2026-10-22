package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

class ModelHeaderTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "5", "5.0", "v5.0.0", "5.0.0-beta"})
    @DisplayName("Версия Archi в заголовке модели — три числа через точку")
    void archiVersionMustBeThreeNumbers(String archiVersion) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new ModelHeader(ModelId.next(UUIDS), WorkspaceId.next(UUIDS), Models.archiId(), "Файл",
                        null, archiVersion, ModelStatus.ACTIVE, List.of(), null, "a", NOW, NOW, 0));

        assertEquals(ModelingMessages.ARCHI_VERSION_FORMAT, rejected.reason().key());
    }
}
