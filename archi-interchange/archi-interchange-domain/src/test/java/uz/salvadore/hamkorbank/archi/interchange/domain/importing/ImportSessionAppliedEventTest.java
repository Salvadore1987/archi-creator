package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.NOW;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.UUIDS;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.WORKSPACE;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

class ImportSessionAppliedEventTest {

    private static final ImportSessionId SESSION = ImportSessionId.next(UUIDS);
    private static final ModelId MODEL = new ModelId(UUIDUtil.uuid("0192f000-0000-7000-8000-0000000000aa"));
    private static final ContentHash HASH = ContentHash.of("<x/>".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("событие принятого импорта не строится без обязательных полей")
    void requiredFieldsAreChecked() {
        assertThrows(NullPointerException.class, () -> event(null, "model.archimate", HASH));
        assertThrows(NullPointerException.class, () -> event(WORKSPACE, null, HASH));
        assertThrows(NullPointerException.class, () -> event(WORKSPACE, "model.archimate", null));
    }

    private static ImportSessionAppliedEvent event(WorkspaceId workspace, String sourceName, ContentHash hash) {
        return new ImportSessionAppliedEvent(SESSION, workspace, MODEL, 1, sourceName, hash, false,
                Map.of(Severity.INFO, 0), 0, NOW);
    }
}
