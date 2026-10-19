package uz.salvadore.hamkorbank.archi.bootstrap.version;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.AcquireLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveVersionCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.SnapshotPurgeCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ElementApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.LockApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.SnapshotRetentionApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.VersionApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Снимок версии — единственная копия, пока Git не настроен. Правило
 * ретеншена есть и включается настройкой, но без доступного Git не трогает ничего.
 */
class SnapshotRetentionIT extends IntegrationTest {

    @Autowired
    ModelApplicationService lifecycle;
    @Autowired
    LockApplicationService locks;
    @Autowired
    ElementApplicationService elements;
    @Autowired
    VersionApplicationService versions;
    @Autowired
    SnapshotRetentionApplicationService retention;
    @Autowired
    ModelingKernel kernel;

    @Test
    @DisplayName("INV-MDL-010: без привязки к Git очистка снимков не трогает ни одного")
    void purgeIsDisabledWithoutGitBinding() {
        ModelId model = ModelId.of(lifecycle.createModel(ARCHITECT,
                new CreateModelCommand(workspace().value(), "Ретеншен", null)).id());
        locks.acquireLock(ARCHITECT, new AcquireLockCommand(model.value()));
        for (int i = 0; i < 3; i++) {
            elements.createElement(ARCHITECT, new CreateElementCommand(model.value(),
                    ArchiType.ofSimpleName("ApplicationComponent").value(), "Система " + i, null, null, null, null));
            versions.saveVersion(ARCHITECT, new SaveVersionCommand(model.value(), "правка " + i, null));
        }
        // Версии «стареют» на полгода и получают git_sha — по правилу ретеншена их снимки лишние.
        jdbc.update("update model_version set created_at = created_at - interval '180 days', git_sha = ? "
                + "where model_id = ?", "a".repeat(40), model.value());

        SnapshotPurgeCommandHandler enabledWithoutGit = new SnapshotPurgeCommandHandler(kernel,
                workspaceId -> false, ZoneOffset.UTC, true);
        SnapshotPurgeCommandHandler enabledWithGit = new SnapshotPurgeCommandHandler(kernel, workspaceId -> true,
                ZoneOffset.UTC, true);

        assertEquals(0, retention.purgeSnapshots(), "у пространства нет привязки к Git");
        assertEquals(0, enabledWithoutGit.handle(), "без Git снимок — единственная копия (FR-47)");
        assertEquals(4, snapshotsOf(model.value()));

        enabledWithGit.handle();
        assertEquals(1, snapshotsOf(model.value()), "с Git остаётся последняя за месяц");
    }

    private int snapshotsOf(UUID model) {
        return jdbc.queryForObject("select count(*) from model_version where model_id = ? and snapshot is not null",
                Integer.class, model);
    }
}
