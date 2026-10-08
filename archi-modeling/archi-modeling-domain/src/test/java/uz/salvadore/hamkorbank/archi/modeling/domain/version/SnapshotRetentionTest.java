package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class SnapshotRetentionTest {

    private static final SnapshotRetention RETENTION = new SnapshotRetention(ZoneOffset.UTC);
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    @Test
    @DisplayName("INV-MDL-010: без привязки к Git правило не отдаёт ни одного снимка (FR-47)")
    void purgeIsDisabledWithoutGitBinding() {
        List<ModelVersion> history = history();

        assertEquals(List.of(), RETENTION.purgeable(history, NOW, false));
        assertTrue(!RETENTION.purgeable(history, NOW, true).isEmpty(), "с Git правило работает");
    }

    @Test
    @DisplayName("FR-46: хранятся 30 дней, помеченные и последняя за месяц")
    void keepsRecentLabelledAndLastOfMonth() {
        List<Long> purgeable = RETENTION.purgeable(history(), NOW, true).stream().map(ModelVersion::versionNo).toList();

        // 1–3 — июль: 3 последняя за месяц, 2 помечена; 4–5 — август: 5 последняя;
        // 6 — в пределах 30 дней.
        assertEquals(List.of(1L, 4L), purgeable);
    }

    private static List<ModelVersion> history() {
        List<ModelVersion> versions = new ArrayList<>();
        String[] dates = {"2026-07-01", "2026-07-10", "2026-07-20", "2026-08-01", "2026-08-15", "2026-10-01"};
        for (int i = 0; i < dates.length; i++) {
            ModelVersion v = new ModelVersion(VersionId.next(Models.UUIDS), ModelVersionTest.MODEL, i + 1, "a",
                    Optional.empty(), i == 1 ? Optional.of("релиз") : Optional.empty(),
                    Instant.parse(dates[i] + "T12:00:00Z"), Optional.of(new byte[] {1}), ModelVersionTest.HASH,
                    Optional.of("c".repeat(40)));
            versions.add(v);
        }
        return versions;
    }
}
