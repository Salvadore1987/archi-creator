package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/** Пределы растра PNG: сторона 16 000 px и площадь — до растеризации, а не после. */
class ViewImageLimitsTest {

    private static final long AREA = 64_000_000;

    @Test
    @DisplayName("UC-IXC-004: растр в пределах стороны, но больше 64 Мпикс по площади — 422 с наибольшей плотностью")
    void areaLimit() {
        InterchangeException failure = assertThrows(InterchangeException.class,
                () -> ViewImages.raster(3_000, 3_000, 4, AREA, "id-view"));

        assertEquals(InterchangeCodes.IMAGE_TOO_LARGE, failure.code());
        assertEquals(Failure.UNPROCESSABLE, failure.failure());
        assertEquals(InterchangeMessages.IMAGE_AREA_TOO_LARGE, failure.reason().key());
        assertEquals(2, failure.details().get("maxScale"), "6 000 × 6 000 = 36 Мпикс — помещается");
        assertEquals("id-view", failure.details().get("viewArchiId"));
    }

    @Test
    @DisplayName("UC-IXC-004: сторона больше 16 000 px — прежний отказ по стороне")
    void sideLimit() {
        InterchangeException failure = assertThrows(InterchangeException.class,
                () -> ViewImages.raster(5_000, 100, 4, AREA, "id-view"));

        assertEquals(InterchangeMessages.IMAGE_TOO_LARGE, failure.reason().key());
        assertEquals(2, failure.details().get("maxScale"));
    }

    @Test
    @DisplayName("UC-IXC-004: не помещается и при плотности 1 — maxScale 0")
    void nothingFits() {
        assertEquals(0, assertThrows(InterchangeException.class,
                () -> ViewImages.raster(9_000, 9_000, 1, AREA, "id-view")).details().get("maxScale"));
    }

    @Test
    @DisplayName("Растр ровно на пределе площади строится; размер — с округлением вверх")
    void rasterAtLimitIsBuilt() {
        assertEquals(new ViewImages.Raster(8_000, 8_000), ViewImages.raster(2_000, 2_000, 4, AREA, "id-view"));
        assertEquals(new ViewImages.Raster(201, 101), ViewImages.raster(100.2, 50.1, 2, AREA, "id-view"));
    }
}
