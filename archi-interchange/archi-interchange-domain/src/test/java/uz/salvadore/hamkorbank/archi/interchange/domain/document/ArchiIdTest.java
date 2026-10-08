package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;

class ArchiIdTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "id-9c7b5378a397030e3357cb9b",          // эталонная модель: 24 hex
            "id-4c1f0f8e2a2b4d3e9f8a7b6c5d4e3f2a",  // Archi UUIDFactory: 32 hex
            "4fe7b3cd",                              // модели старых версий Archi
            "id-b2c8_a.1"})
    @DisplayName("ADR-0002: принимается любой идентификатор, который мог записать Archi")
    void archiIdsAreAcceptedLiterally(String value) {
        assertEquals(value, ArchiId.of(value).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " id-1", "id 1", "id-\"1", "-id", "id<1>"})
    @DisplayName("значение, ломающее атрибут id, отклоняется")
    void malformedIdsAreRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> ArchiId.of(value));
    }
}
