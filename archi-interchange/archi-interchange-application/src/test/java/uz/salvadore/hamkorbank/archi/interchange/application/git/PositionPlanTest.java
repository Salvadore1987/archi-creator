package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PositionPlanTest {

    @Test
    @DisplayName("INV-IXC-011: без прежних позиций — шаг 1000 по порядку")
    void freshContainerIsSparse() {
        assertArrayEquals(new long[]{1000, 2000, 3000}, PositionPlan.plan(List.of("a", "b", "c"), Map.of()));
    }

    @Test
    @DisplayName("INV-IXC-011: вставка встаёт в промежуток, соседи сохраняют позиции")
    void insertionFillsGap() {
        assertArrayEquals(new long[]{1000, 1500, 2000, 3000},
                PositionPlan.plan(List.of("a", "new", "b", "c"), Map.of("a", 1000L, "b", 2000L, "c", 3000L)));
    }

    @Test
    @DisplayName("INV-IXC-011: в хвосте — следующая тысяча после последней позиции")
    void appendGoesAfterLast() {
        assertArrayEquals(new long[]{1000, 2500, 3000, 4000},
                PositionPlan.plan(List.of("a", "b", "x", "y"), Map.of("a", 1000L, "b", 2500L)));
    }

    @Test
    @DisplayName("INV-IXC-011: при перестановке меняется одна позиция, остальные — прежние")
    void movedEntryIsTheOnlyOneRenumbered() {
        assertArrayEquals(new long[]{500, 1000, 3000},
                PositionPlan.plan(List.of("b", "a", "c"), Map.of("a", 1000L, "b", 2000L, "c", 3000L)));
    }

    @Test
    @DisplayName("INV-IXC-011: хвост за наибольшей позицией не переполняется — контейнер нумеруется заново")
    void tailNearLongMaxRenumbers() {
        assertArrayEquals(new long[]{1000, 2000},
                PositionPlan.plan(List.of("a", "new"), Map.of("a", Long.MAX_VALUE)));
    }

    @Test
    @DisplayName("INV-IXC-011: широкий промежуток делится без переполнения, позиции растут")
    void wideGapDividesWithoutOverflow() {
        long[] plan = PositionPlan.plan(List.of("a", "x", "y", "b"), Map.of("a", 1L, "b", Long.MAX_VALUE));
        assertEquals(1L, plan[0]);
        assertEquals(Long.MAX_VALUE, plan[3]);
        assertTrue(plan[0] < plan[1] && plan[1] < plan[2] && plan[2] < plan[3]);
    }

    @Test
    @DisplayName("INV-IXC-011: промежутка не хватает — контейнер нумеруется заново")
    void noRoomRenumbers() {
        assertArrayEquals(new long[]{1000, 2000, 3000},
                PositionPlan.plan(List.of("a", "new", "b"), Map.of("a", 1L, "b", 2L)));
    }
}
