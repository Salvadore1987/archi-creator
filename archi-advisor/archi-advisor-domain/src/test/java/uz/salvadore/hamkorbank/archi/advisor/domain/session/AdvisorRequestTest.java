package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;

class AdvisorRequestTest {

    @Test
    @DisplayName("Подсказке нужен элемент, плану — описание не длиннее предела")
    void functionsRequireTheirParameters() {
        assertThrows(InvalidValueException.class, () -> new AdvisorRequest(AdvisorFunction.EXPLAIN, Sessions.MODEL,
                null, null, null));
        assertThrows(InvalidValueException.class, () -> AdvisorRequest.plan(Sessions.MODEL, "   "));
        assertThrows(InvalidValueException.class, () -> AdvisorRequest.plan(Sessions.MODEL, "a".repeat(4001)));
    }

    @Test
    @DisplayName("Отпечаток зависит от параметров и не содержит текста описания")
    void fingerprintIsStableAndOpaque() {
        String first = AdvisorRequest.plan(Sessions.MODEL, "Платёжный шлюз").fingerprint();

        assertEquals(first, AdvisorRequest.plan(Sessions.MODEL, " Платёжный шлюз ").fingerprint());
        assertNotEquals(first, AdvisorRequest.plan(Sessions.MODEL, "Кредитный конвейер").fingerprint());
        assertEquals(64, first.length());
    }

    @Test
    @DisplayName("идентификатор элемента — вида идентификатора Archi: строка любой длины в сессию не попадает")
    void elementIdIsArchiId() {
        assertThrows(InvalidValueException.class, () -> AdvisorRequest.explain(Sessions.MODEL, "a".repeat(10_000)));
        assertThrows(InvalidValueException.class, () -> AdvisorRequest.explain(Sessions.MODEL, "id 1"));
        assertThrows(InvalidValueException.class,
                () -> AdvisorRequest.document(Sessions.MODEL, null, "<script>"));
        assertEquals("id-e1", AdvisorRequest.explain(Sessions.MODEL, "id-e1").elementId());
    }
}
