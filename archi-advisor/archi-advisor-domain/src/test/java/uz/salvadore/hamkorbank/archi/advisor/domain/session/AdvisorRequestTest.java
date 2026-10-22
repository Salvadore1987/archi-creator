package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;

class AdvisorRequestTest {

    @Test
    @DisplayName("Подсказке нужен элемент")
    void explainRequiresElement() {
        assertThrows(InvalidValueException.class, () -> new AdvisorRequest(AdvisorFunction.EXPLAIN, Sessions.MODEL,
                null, null, null));
    }

    @Test
    @DisplayName("Плану нужно непустое описание")
    void planRequiresDescription() {
        assertThrows(InvalidValueException.class, () -> AdvisorRequest.plan(Sessions.MODEL, "   "));
    }

    @Test
    @DisplayName("Описание плана не длиннее предела")
    void planDescriptionLongerThanLimitIsRejected() {
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

    @ParameterizedTest
    @MethodSource("malformedElementIds")
    @DisplayName("идентификатор элемента подсказки — вида Archi: строка любой длины в сессию не попадает")
    void explainElementIdIsArchiId(String elementId) {
        assertThrows(InvalidValueException.class, () -> AdvisorRequest.explain(Sessions.MODEL, elementId));
    }

    @Test
    @DisplayName("идентификатор элемента документации — вида идентификатора Archi")
    void documentElementIdIsArchiId() {
        assertThrows(InvalidValueException.class,
                () -> AdvisorRequest.document(Sessions.MODEL, null, "<script>"));
    }

    @Test
    @DisplayName("идентификатор Archi принимается идентификатором элемента")
    void archiIdIsAcceptedAsElementId() {
        assertEquals("id-e1", AdvisorRequest.explain(Sessions.MODEL, "id-e1").elementId());
    }

    @ParameterizedTest
    @MethodSource("foreignParameters")
    @DisplayName("параметр, который функции не нужен, — отказ: иначе он меняет отпечаток и пишется в сессию")
    void foreignParametersAreRejected(AdvisorFunction function, ViewId view, String elementId, String description) {
        assertThrows(InvalidValueException.class,
                () -> new AdvisorRequest(function, Sessions.MODEL, view, elementId, description));
    }

    @Test
    @DisplayName("ревью принимает представление")
    void reviewTakesView() {
        ViewId view = ViewId.of(Sessions.UUIDS.next());

        assertEquals(view, AdvisorRequest.review(Sessions.MODEL, view).viewId());
    }

    @Test
    @DisplayName("Документация — по представлению или по элементу, но не по обоим сразу")
    void documentWithViewAndElementIsRejected() {
        ViewId view = ViewId.of(Sessions.UUIDS.next());

        InvalidValueException refused = assertThrows(InvalidValueException.class,
                () -> AdvisorRequest.document(Sessions.MODEL, view, "id-e1"));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.DOCUMENT_TARGET_AMBIGUOUS), refused.reason());
    }

    static Stream<String> malformedElementIds() {
        return Stream.of("a".repeat(10_000), "id 1");
    }

    static Stream<Arguments> foreignParameters() {
        ViewId view = ViewId.of(Sessions.UUIDS.next());
        return Stream.of(
                Arguments.of(AdvisorFunction.REVIEW, null, "id-e1", null),
                Arguments.of(AdvisorFunction.REVIEW, null, null, "план"),
                Arguments.of(AdvisorFunction.EXPLAIN, view, "id-e1", null),
                Arguments.of(AdvisorFunction.PLAN, null, "id-e1", "план"),
                Arguments.of(AdvisorFunction.DOCUMENT, view, null, "план"));
    }
}
