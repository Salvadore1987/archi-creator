package uz.salvadore.hamkorbank.archi.modeling.domain.version;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Правило ретеншена снимков: сохраняются снимки
 * последних 30 дней, всех помеченных версий и последней версии каждого календарного
 * месяца. Остальные можно очистить — но только при настроенном Git:
 * без него снимок — единственная копия, и правило не отдаёт ни одного.
 */
public final class SnapshotRetention {

    public static final Duration RECENT = Duration.ofDays(30);

    private final ZoneId zone;

    public SnapshotRetention(ZoneId zone) {
        this.zone = zone;
    }

    /** Версии одной модели, чьи снимки разрешено очистить. */
    public List<ModelVersion> purgeable(Collection<ModelVersion> versions, Instant now, boolean gitBound) {
        if (!gitBound) {
            return List.of();
        }
        Map<YearMonth, ModelVersion> lastOfMonth = new HashMap<>();
        for (ModelVersion version : versions) {
            lastOfMonth.merge(YearMonth.from(version.createdAt().atZone(zone)), version,
                    (a, b) -> a.versionNo() >= b.versionNo() ? a : b);
        }
        Instant horizon = now.minus(RECENT);
        return versions.stream()
                .filter(v -> v.snapshot().isPresent())
                .filter(v -> v.gitSha().isPresent())
                .filter(v -> v.label().isEmpty())
                .filter(v -> v.createdAt().isBefore(horizon))
                .filter(v -> !lastOfMonth.get(YearMonth.from(v.createdAt().atZone(zone))).equals(v))
                .sorted(Comparator.comparingLong(ModelVersion::versionNo))
                .toList();
    }
}
