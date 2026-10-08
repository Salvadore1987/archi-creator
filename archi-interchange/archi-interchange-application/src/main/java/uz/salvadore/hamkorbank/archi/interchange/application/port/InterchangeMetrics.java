package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;

/** Метрики interchange (spec/nfr/interchange.yaml#observability). */
public interface InterchangeMetrics {

    <T> T observe(String useCase, Supplier<T> work);

    /**
     * {@code interchange_import_sessions_total{outcome, strict_mode}} и
     * {@code interchange_import_findings_total{severity, code}}.
     */
    default void importFinished(String outcome, boolean strictMode, List<ImportFinding> findings) {
    }

    /** {@code interchange_opaque_objects{model_id}}: сколько объектов сохранено непрозрачными (FR-03). */
    default void opaqueObjects(UUID modelId, int count) {
    }

    /** {@code interchange_export_loss_entries_total{format, object_kind}}; ноль — тоже отсчёт. */
    default void exportLosses(String format, int entries) {
    }

    InterchangeMetrics NONE = new InterchangeMetrics() {
        @Override
        public <T> T observe(String useCase, Supplier<T> work) {
            return work.get();
        }
    };
}
