package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.ShapeEntry;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Size;

class OutlinesTest {

    @Test
    @DisplayName("UI-019: прямоугольник со срезанными углами — тот же путь, что рисует канва (shapes.test.ts)")
    void bevelMatchesCanvas() {
        ShapeEntry bevel = new ShapeEntry("bevel", Optional.empty(), Optional.empty(), "node_radius",
                Optional.of("motivation"), Optional.empty());

        assertEquals("M8.5 0.5 H111.5 L119.5 8.5 V46.5 L111.5 54.5 H8.5 L0.5 46.5 V8.5 Z",
                Outlines.path(bevel, new Size(120, 55), 4));
    }
}
