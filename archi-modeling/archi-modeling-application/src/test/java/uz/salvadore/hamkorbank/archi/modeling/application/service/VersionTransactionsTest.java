package uz.salvadore.hamkorbank.archi.modeling.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

class VersionTransactionsTest {

    private static final EditorIdentity ALICE = EditorIdentity.of("alice", Role.ARCHITECT);
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    private final InMemoryPorts ports = new InMemoryPorts();
    /** Был ли каждый внешний вызов сделан внутри транзакции. */
    private final List<Boolean> externalCallsInTransaction = new ArrayList<>();
    private final VersionService versions = new VersionService(ports.kernel, ports.snapshotWriter,
            (xml, current, now) -> new ModelContent(ports.modelRepository.load(current.id()).orElseThrow(), List.of()),
            workspaceId -> external(true),
            (model, version) -> external(Optional.of("restored".getBytes(StandardCharsets.UTF_8))),
            ZoneOffset.UTC, true);

    @Test
    @DisplayName("выгрузка очищенного снимка обращается к Git вне транзакции")
    void snapshotFetchesOutsideTransaction() {
        ModelId model = purgedModel();

        versions.snapshot(ALICE, model, 1L);

        assertEquals(List.of(false), externalCallsInTransaction);
    }

    @Test
    @DisplayName("откат к очищенной версии обращается к Git вне транзакции записи")
    void rollbackFetchesOutsideTransaction() {
        ModelId model = purgedModel();
        ports.lock(ALICE, model);

        versions.rollback(ALICE, model, 1, null);

        assertEquals(List.of(false), externalCallsInTransaction);
    }

    @Test
    @DisplayName("FR-47: очистка снимков проверяет доступность Git вне транзакции")
    void purgeChecksGitOutsideTransaction() {
        ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", null);
        ModelVersion first = ports.versions.removeFirst();
        ports.versions.add(first.publishedAs(SHA));

        versions.purgeSnapshots();

        assertFalse(externalCallsInTransaction.isEmpty());
        assertFalse(externalCallsInTransaction.contains(true));
    }

    private ModelId purgedModel() {
        ModelId model = ports.lifecycle.create(ALICE, ports.workspace.id(), "Ландшафт", null).id();
        ModelVersion first = ports.versions.removeFirst();
        ports.versions.add(first.publishedAs(SHA).snapshotPurged(true));
        return model;
    }

    private <T> T external(T result) {
        externalCallsInTransaction.add(ports.inTransaction());
        return result;
    }
}
