package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArchiTypeTest {

    @Test
    @DisplayName("FR-03: тип, которого метамодель не знает, всё равно принимается")
    void unknownTypeIsAccepted() {
        ArchiType type = ArchiType.of("archimate:SomethingFromArchiNext");

        assertEquals("SomethingFromArchiNext", type.simpleName());
    }

    @Test
    @DisplayName("короткое имя и полная нотация дают один и тот же тип")
    void simpleNameRoundTrips() {
        assertEquals(ArchiType.of("archimate:ApplicationComponent"),
                ArchiType.ofSimpleName("ApplicationComponent"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ApplicationComponent", "archimate:", "archimate:Application Component",
            "xsi:ApplicationComponent", "archimate:Component1"})
    @DisplayName("строка не в нотации archimate:<Имя> отклоняется")
    void malformedTypeIsRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> ArchiType.of(value));
    }
}
