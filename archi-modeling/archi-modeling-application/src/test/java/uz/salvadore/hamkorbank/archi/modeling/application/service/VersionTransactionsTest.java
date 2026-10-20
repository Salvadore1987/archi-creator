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
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RestoreVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetVersionSnapshotQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.SnapshotPurgeCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionContents;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionRestoreCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.VersionSnapshotGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelRowsMapper;
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
    /** Модель, чей снимок вернёт чтение: содержимое отката — её же текущее. */
    private ModelId current;
    private final VersionContents contents = ports.versionContents(
            xml -> ModelRowsMapper.rows(new ModelContent(ports.modelRepository.load(current).orElseThrow(),
                    List.of())),
            (model, version) -> external(Optional.of("restored".getBytes(StandardCharsets.UTF_8))));

    @Test
    @DisplayName("выгрузка очищенного снимка обращается к Git вне транзакции")
    void snapshotFetchesOutsideTransaction() {
        ModelId model = purgedModel();

        new VersionSnapshotGetQueryHandler(ports.kernel, contents).handle(ALICE,
                new GetVersionSnapshotQuery(model.value(), 1L));

        assertEquals(List.of(false), externalCallsInTransaction);
    }

    @Test
    @DisplayName("откат к очищенной версии обращается к Git вне транзакции записи")
    void rollbackFetchesOutsideTransaction() {
        ModelId model = purgedModel();
        ports.lock(ALICE, model);

        new VersionRestoreCommandHandler(ports.kernel, contents).handle(ALICE,
                new RestoreVersionCommand(model.value(), 1, null));

        assertEquals(List.of(false), externalCallsInTransaction);
    }

    @Test
    @DisplayName("FR-47: очистка снимков проверяет доступность Git вне транзакции")
    void purgeChecksGitOutsideTransaction() {
        ports.createModel(ALICE, "Ландшафт");
        ModelVersion first = ports.versions.removeFirst();
        ports.versions.add(first.publishedAs(SHA));

        new SnapshotPurgeCommandHandler(ports.kernel, workspaceId -> external(true), ZoneOffset.UTC, true).handle();

        assertFalse(externalCallsInTransaction.isEmpty());
        assertFalse(externalCallsInTransaction.contains(true));
    }

    private ModelId purgedModel() {
        ModelId model = ports.createModel(ALICE, "Ландшафт");
        ModelVersion first = ports.versions.removeFirst();
        ports.versions.add(first.publishedAs(SHA).snapshotPurged(true));
        current = model;
        return model;
    }

    private <T> T external(T result) {
        externalCallsInTransaction.add(ports.inTransaction());
        return result;
    }
}
