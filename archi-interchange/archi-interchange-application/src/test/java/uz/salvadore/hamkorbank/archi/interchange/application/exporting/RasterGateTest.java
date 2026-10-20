package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

class RasterGateTest {

    private final RasterGate gate = new RasterGate(64_000_000, 1, Duration.ofMillis(50));

    @Test
    @DisplayName("Растеризация, пока идёт другая и ожидание вышло, — 503 IXC_RASTERIZER_BUSY")
    void busyRasterizerRefusesAfterWait() {
        InterchangeException busy = assertThrows(InterchangeException.class,
                () -> gate.through(() -> gate.through(() -> "вторая")));

        assertEquals(InterchangeCodes.RASTERIZER_BUSY, busy.code());
        assertEquals(Failure.UNAVAILABLE, busy.failure());
    }

    @Test
    @DisplayName("Растеризатор освобождается и после отказа внутри: следующая идёт без ожидания")
    void slotIsReleasedAfterFailure() {
        assertThrows(IllegalStateException.class, () -> gate.through(() -> {
            throw new IllegalStateException("растр");
        }));

        assertEquals("следующая", gate.through(() -> "следующая"));
    }
}
