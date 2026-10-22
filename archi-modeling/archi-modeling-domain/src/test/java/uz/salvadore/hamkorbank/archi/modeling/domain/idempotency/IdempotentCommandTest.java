package uz.salvadore.hamkorbank.archi.modeling.domain.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

class IdempotentCommandTest {

    private final Map<String, IdempotencyRecord> records = new HashMap<>();
    private final List<Long> versions = new ArrayList<>();

    @Test
    @DisplayName("INV-MDL-003: повтор с тем же ключом отдаёт первый результат без новой версии")
    void replayReturnsFirstResultWithoutNewVersion() {
        String first = save("key-1", "сохранить отчёт");
        String replay = save("key-1", "сохранить отчёт");

        assertEquals(first, replay);
        assertEquals(List.of(1L), versions, "вторая версия не создана");

        ModelingDomainException conflict = assertThrows(ModelingDomainException.class,
                () -> save("key-1", "другое тело"));
        assertEquals("INV-MDL-003", conflict.code());
        assertEquals(Failure.CONFLICT, conflict.failure());
        assertEquals(List.of(1L), versions);
    }

    @Test
    @DisplayName("INV-MDL-003: отпечаток различает поля, а не их склейку")
    void fingerprintSeparatesFields() {
        assertEquals(IdempotentCommand.fingerprint("a", Optional.of("b")), IdempotentCommand.fingerprint("a", "b"));
        assertInstanceOf(IdempotentCommand.Execute.class, IdempotentCommand.decide(null, "x"));
        assertEquals(false, IdempotentCommand.fingerprint("ab", "c").equals(IdempotentCommand.fingerprint("a", "bc")));
    }

    /** Команда «сохранить версию» в миниатюре: решение домена плюс запись результата. */
    private String save(String key, String comment) {
        String fingerprint = IdempotentCommand.fingerprint("SaveModel", comment);
        return switch (IdempotentCommand.decide(records.get(key), fingerprint)) {
            case IdempotentCommand.Replay replay -> replay.resultRef();
            case IdempotentCommand.Execute execute -> {
                long versionNo = versions.size() + 1L;
                versions.add(versionNo);
                records.put(key, IdempotencyRecord.restore("SaveModel", "architect-1", key, fingerprint,
                        String.valueOf(versionNo), Instant.EPOCH));
                yield String.valueOf(versionNo);
            }
        };
    }

    @Test
    @DisplayName("Отпечаток различает «значения нет» и строку \"null\"")
    void absentDiffersFromNullText() {
        String text = IdempotentCommand.fingerprint("a", "null");
        String absent = IdempotentCommand.fingerprint("a", Optional.empty());

        assertNotEquals(text, absent);
        assertNotEquals(text, IdempotentCommand.fingerprint("a", (Object) null));
        assertEquals(absent, IdempotentCommand.fingerprint("a", (Object) null));
    }

    /** Составное значение команды: его {@code toString} — не контракт отпечатка. */
    private record Composite(String name, int size) {
    }

    @Test
    @DisplayName("INV-MDL-003: отпечаток — из простых значений, составное значение — ошибка программиста")
    void fingerprintTakesPlainValuesOnly() {
        assertThrows(IllegalArgumentException.class, () -> IdempotentCommand.fingerprint("a", new Composite("x", 1)));
        assertEquals(IdempotentCommand.fingerprint("a", 1, true, null),
                IdempotentCommand.fingerprint("a", 1, true, null));
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    @DisplayName("Ключ идемпотентности не пустой и не длиннее предела")
    void invalidKeyIsRejected(String key) {
        ModelingDomainException rejected = assertThrows(ModelingDomainException.class,
                () -> IdempotentCommand.requireValidKey(key));

        assertEquals(ModelingCodes.INVALID_INPUT, rejected.code());
        assertEquals(ModelingMessages.IDEMPOTENCY_KEY_INVALID, rejected.reason().key());
    }

    static Stream<String> invalidKeys() {
        return Stream.of(null, "", "   ", "k".repeat(IdempotencyRecord.KEY_MAX + 1));
    }
}
