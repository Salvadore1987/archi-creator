package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PropertyEntryTest {

    @Test
    @DisplayName("Ключ свойства не длиннее предела")
    void keyLongerThanLimitIsRejected() {
        String key = "к".repeat(PropertyEntry.KEY_MAX + 1);

        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> new PropertyEntry(key, "значение", SortOrder.ofPosition(0)));

        assertEquals(ModelingMessages.PROPERTY_KEY_TOO_LONG, rejected.reason().key());
    }
}
