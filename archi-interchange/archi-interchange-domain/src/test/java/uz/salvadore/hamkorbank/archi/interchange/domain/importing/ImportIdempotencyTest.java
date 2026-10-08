package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.NOW;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.VALID;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImportIdempotencyTest {

    /** Хранилище сессий в памяти: ключ — рабочее пространство и ключ идемпотентности. */
    private final Map<String, ImportSession> stored = new HashMap<>();
    private final ImportIntake intake = new ImportIntake(
            (workspace, key) -> Optional.ofNullable(stored.get(workspace + "/" + key)),
            Sessions.UUIDS, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("INV-IXC-003: тот же ключ и тот же файл возвращают существующую сессию")
    void sameKeyReturnsExistingSession() {
        ImportIntake.Intake first = intake.receive(Sessions.request(VALID, "retry-1", false));
        store(first.session());

        ImportIntake.Intake retry = intake.receive(Sessions.request(VALID, "retry-1", false));

        assertFalse(first.replayed());
        assertTrue(retry.replayed());
        assertSame(first.session(), retry.session());
    }

    @Test
    @DisplayName("INV-IXC-003: тот же ключ с другим файлом — конфликт, а не новый импорт")
    void sameKeyDifferentFileIsConflict() {
        store(intake.receive(Sessions.request(VALID, "retry-1", false)).session());

        IdempotencyConflictException conflict = assertThrows(IdempotencyConflictException.class,
                () -> intake.receive(Sessions.request(VALID + "\n", "retry-1", false)));

        assertEquals("retry-1", conflict.existing().idempotencyKey());
    }

    @Test
    @DisplayName("другой ключ — новая сессия, даже для того же файла")
    void differentKeyStartsNewSession() {
        ImportSession first = intake.receive(Sessions.request(VALID, "a", false)).session();
        store(first);

        ImportIntake.Intake second = intake.receive(Sessions.request(VALID, "b", false));

        assertFalse(second.replayed());
        assertNotEquals(first.id(), second.session().id());
        assertEquals(ImportStatus.RECEIVED, second.session().status());
    }

    private void store(ImportSession session) {
        stored.put(session.workspaceId() + "/" + session.idempotencyKey(), session);
    }
}
