package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.VALID;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.WORKSPACE;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

class ImportRequestTest {

    private static final byte[] CONTENT = VALID.getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest
    @MethodSource("invalidSourceNames")
    @DisplayName("имя файла импорта — непустое и не длиннее предела")
    void blankOrTooLongSourceNameIsRejected(String sourceName) {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> ImportRequest.of(WORKSPACE, null, sourceName, CONTENT, "key-1", false, "architect"));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.SOURCE_NAME_INVALID, e.reason().key());
        assertEquals(List.of(ImportRequest.SOURCE_NAME_MAX), e.reason().args());
    }

    @Test
    @DisplayName("пустой файл на импорт не принимается")
    void emptySourceIsRejected() {
        byte[] empty = new byte[0];

        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> ImportRequest.of(WORKSPACE, null, "model.archimate", empty, "key-1", false, "architect"));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.SOURCE_EMPTY, e.reason().key());
    }

    @ParameterizedTest
    @MethodSource("invalidIdempotencyKeys")
    @DisplayName("ключ идемпотентности — непустой и не длиннее предела")
    void blankOrTooLongIdempotencyKeyIsRejected(String key) {
        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> ImportRequest.of(WORKSPACE, null, "model.archimate", CONTENT, key, false, "architect"));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.IDEMPOTENCY_KEY_INVALID, e.reason().key());
        assertEquals(List.of(ImportRequest.IDEMPOTENCY_KEY_MAX), e.reason().args());
    }

    static Stream<String> invalidSourceNames() {
        return Stream.of("", "   ", "m".repeat(ImportRequest.SOURCE_NAME_MAX + 1));
    }

    static Stream<String> invalidIdempotencyKeys() {
        return Stream.of("", "   ", "k".repeat(ImportRequest.IDEMPOTENCY_KEY_MAX + 1));
    }
}
