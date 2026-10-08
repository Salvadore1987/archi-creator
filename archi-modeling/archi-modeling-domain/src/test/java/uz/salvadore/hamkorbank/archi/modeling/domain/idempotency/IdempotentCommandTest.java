package uz.salvadore.hamkorbank.archi.modeling.domain.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;

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

        ModelingException conflict = assertThrows(ModelingException.class, () -> save("key-1", "другое тело"));
        assertEquals("INV-MDL-003", conflict.code());
        assertEquals(Failure.CONFLICT, conflict.failure());
        assertEquals(List.of(1L), versions);
    }

    @Test
    @DisplayName("INV-MDL-003: отпечаток различает поля, а не их склейку")
    void fingerprintSeparatesFields() {
        assertEquals(IdempotentCommand.fingerprint("a", Optional.of("b")), IdempotentCommand.fingerprint("a", "b"));
        assertInstanceOf(IdempotentCommand.Execute.class, IdempotentCommand.decide(Optional.empty(), "x"));
        assertEquals(false, IdempotentCommand.fingerprint("ab", "c").equals(IdempotentCommand.fingerprint("a", "bc")));
    }

    /** Команда «сохранить версию» в миниатюре: решение домена плюс запись результата. */
    private String save(String key, String comment) {
        String fingerprint = IdempotentCommand.fingerprint("SaveModel", comment);
        return switch (IdempotentCommand.decide(Optional.ofNullable(records.get(key)), fingerprint)) {
            case IdempotentCommand.Replay replay -> replay.resultRef();
            case IdempotentCommand.Execute execute -> {
                long versionNo = versions.size() + 1L;
                versions.add(versionNo);
                records.put(key, new IdempotencyRecord("SaveModel", "architect-1", key, fingerprint,
                        String.valueOf(versionNo), Instant.EPOCH));
                yield String.valueOf(versionNo);
            }
        };
    }
}
