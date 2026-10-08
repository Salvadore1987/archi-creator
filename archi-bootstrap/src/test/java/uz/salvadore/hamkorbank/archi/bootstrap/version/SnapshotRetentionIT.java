package uz.salvadore.hamkorbank.archi.bootstrap.version;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.interchange.application.mapping.ArchimateSnapshots;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ElementService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.LockService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelLifecycleService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.application.service.VersionService;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * INV-MDL-010, FR-47: снимок — единственная копия, пока Git не настроен. Правило
 * ретеншена есть и включается настройкой, но без доступного Git не трогает ничего.
 */
class SnapshotRetentionIT extends IntegrationTest {

    @Autowired
    ModelLifecycleService lifecycle;
    @Autowired
    LockService locks;
    @Autowired
    ElementService elements;
    @Autowired
    VersionService versions;
    @Autowired
    ModelingKernel kernel;
    @Autowired
    ArchimateSnapshots snapshots;

    @Test
    @DisplayName("INV-MDL-010: без привязки к Git очистка снимков не трогает ни одного")
    void purgeIsDisabledWithoutGitBinding() {
        ModelId model = lifecycle.create(ARCHITECT, workspace(), "Ретеншен", Optional.empty()).id();
        locks.acquire(ARCHITECT, model);
        for (int i = 0; i < 3; i++) {
            elements.create(ARCHITECT, model, ArchiType.ofSimpleName("ApplicationComponent"), "Система " + i,
                    Optional.empty(), Optional.empty());
            versions.save(ARCHITECT, model, Optional.of("правка " + i), Optional.empty());
        }
        // Версии «стареют» на полгода и получают git_sha — по правилу §4.4 их снимки уже лишние.
        jdbc.update("update model_version set created_at = created_at - interval '180 days', git_sha = ? "
                + "where model_id = ?", "a".repeat(40), model.value());

        VersionService enabledWithoutGit = new VersionService(kernel, snapshots, snapshots, workspaceId -> false,
                ZoneOffset.UTC, true);
        VersionService enabledWithGit = new VersionService(kernel, snapshots, snapshots, workspaceId -> true,
                ZoneOffset.UTC, true);

        assertEquals(0, versions.purgeSnapshots(), "по умолчанию очистка выключена до этапа 7a");
        assertEquals(0, enabledWithoutGit.purgeSnapshots(), "без Git снимок — единственная копия (FR-47)");
        assertEquals(4, snapshotsOf(model.value()));

        enabledWithGit.purgeSnapshots();
        assertEquals(1, snapshotsOf(model.value()), "с Git остаётся последняя за месяц");
    }

    private int snapshotsOf(UUID model) {
        return jdbc.queryForObject("select count(*) from model_version where model_id = ? and snapshot is not null",
                Integer.class, model);
    }
}
