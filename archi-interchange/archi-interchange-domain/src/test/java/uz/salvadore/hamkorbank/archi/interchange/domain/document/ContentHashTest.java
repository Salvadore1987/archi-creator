package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentHashTest {

    @Test
    @DisplayName("SHA-256 содержимого в нижнем регистре")
    void hashesContent() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ContentHash.of("abc".getBytes(StandardCharsets.US_ASCII)).value());
    }

    @Test
    @DisplayName("строка не в формате SHA-256 отклоняется")
    void rejectsMalformedHash() {
        assertThrows(IllegalArgumentException.class, () -> new ContentHash("ABC"));
    }
}
