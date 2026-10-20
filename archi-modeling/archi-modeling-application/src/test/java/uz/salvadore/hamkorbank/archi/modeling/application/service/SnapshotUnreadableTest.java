package uz.salvadore.hamkorbank.archi.modeling.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionContents;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionRestoreCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotArchive;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotUnreadableException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** Откат к версии, чей снимок не раскладывается: испорчен собственный снимок — дефект сервера с кодом. */
class SnapshotUnreadableTest {

    private static final EditorIdentity ALICE = EditorIdentity.of("alice", Role.ARCHITECT);

    private final InMemoryPorts ports = new InMemoryPorts();
    private final IllegalStateException cause = new IllegalStateException("раскладка");
    private final VersionContents contents = ports.versionContents(xml -> {
        throw new SnapshotUnreadableException(cause);
    }, SnapshotArchive.NONE);

    @Test
    @DisplayName("Откат к нераскладываемому снимку — 500 MDL_SNAPSHOT_CORRUPTED с причиной, модель не меняется")
    void rollbackToUnreadableSnapshotIsDefect() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ports.lock(ALICE, model);
        int versions = ports.versions.size();

        ModelingDomainException failure = assertThrows(ModelingDomainException.class,
                () -> new VersionRestoreCommandHandler(ports.kernel, contents).handle(ALICE,
                        new RestoreVersionCommand(model.value(), 1, null)));

        assertInstanceOf(SnapshotUnreadableException.class, failure);
        assertEquals(ModelingCodes.SNAPSHOT_CORRUPTED, failure.code());
        assertEquals(Failure.DEFECT, failure.failure());
        assertEquals(cause, failure.getCause());
        assertEquals(versions, ports.versions.size(), "новой версии нет");
    }
}
