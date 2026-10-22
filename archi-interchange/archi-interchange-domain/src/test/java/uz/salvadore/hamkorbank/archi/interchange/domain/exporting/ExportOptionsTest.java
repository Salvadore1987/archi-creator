package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

class ExportOptionsTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.5"})
    @DisplayName("масштаб выгрузки картинки — только положительный")
    void nonPositiveScaleIsRejected(String scale) {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> new ExportOptions(new BigDecimal(scale), null, null, null, null));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.EXPORT_SCALE_POSITIVE, e.reason().key());
    }
}
