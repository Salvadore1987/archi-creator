package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

/**
 * Формат выгрузки. Определяет, обязан ли отчёт о потерях быть пустым:
 * у {@link #ARCHIMATE} и {@link #GIT_YAML} — да (round-trip), у остальных он
 * заполняется по факту.
 */
public enum ExportFormat {
    ARCHIMATE(true),
    OPEN_EXCHANGE(false),
    CSV_CATALOG(false),
    SVG(false),
    PNG(false),
    GIT_YAML(true);

    private final boolean lossless;

    ExportFormat(boolean lossless) {
        this.lossless = lossless;
    }

    /** Формат обещает round-trip без потерь: непустой отчёт для него — дефект, а не результат. */
    public boolean lossless() {
        return lossless;
    }
}
