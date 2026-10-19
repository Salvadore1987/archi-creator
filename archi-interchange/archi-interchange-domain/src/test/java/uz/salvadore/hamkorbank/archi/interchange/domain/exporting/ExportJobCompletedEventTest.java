package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ExportJobId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

class ExportJobCompletedEventTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:00:00Z");
    private static final UuidV7 UUIDS = new UuidV7(Clock.fixed(NOW, ZoneOffset.UTC));
    private static final WorkspaceId WORKSPACE =
            new WorkspaceId(UUIDUtil.uuid("0192f000-0000-7000-8000-000000000001"));
    private static final ModelId MODEL = new ModelId(UUIDUtil.uuid("0192f000-0000-7000-8000-0000000000aa"));
    private static final ContentHash HASH = ContentHash.of("<x/>".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("событие выгрузки не строится без пространства; представление необязательно")
    void requiredFieldsAreChecked() {
        assertThrows(NullPointerException.class, () -> event(null));
        assertDoesNotThrow(() -> event(WORKSPACE));
    }

    private static ExportJobCompletedEvent event(WorkspaceId workspace) {
        return new ExportJobCompletedEvent(ExportJobId.next(UUIDS), workspace, MODEL, null, ExportFormat.ARCHIMATE,
                1, HASH, 4, 0, NOW);
    }
}
