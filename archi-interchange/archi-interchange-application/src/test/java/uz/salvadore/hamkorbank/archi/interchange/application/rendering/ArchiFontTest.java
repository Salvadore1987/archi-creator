package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchiFontTest {

    @Test
    @DisplayName("строка FontData: семейство, кегль, начертание битами; начертания нет — обычное")
    void parsesFontData() {
        assertEquals(Optional.of(new ArchiFont("Segoe UI", 10, true, true)),
                ArchiFont.parse("1|Segoe UI|10.0|3|WINDOWS|1|-13|0|0|0|700|0|0|0|0|3|2|1|34|Segoe UI"));
        assertEquals(Optional.of(new ArchiFont("Arial", 9.5, false, false)), ArchiFont.parse("1|Arial|9.5"));
        assertEquals(Optional.of(new ArchiFont("Arial", 9, false, true)), ArchiFont.parse("1|Arial|9| 2 "));
    }

    @Test
    @DisplayName("без семейства или кегля, с нечисловым кеглем или начертанием — пусто")
    void rejectsMalformed() {
        assertEquals(Optional.empty(), ArchiFont.parse("1|Arial"));
        assertEquals(Optional.empty(), ArchiFont.parse("1| |10.0"));
        assertEquals(Optional.empty(), ArchiFont.parse("1|Arial|десять"));
        assertEquals(Optional.empty(), ArchiFont.parse("1|Arial|10.0|bold"));
    }
}
