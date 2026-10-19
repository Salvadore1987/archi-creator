package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Формат выгрузки. Определяет, обязан ли отчёт о потерях быть пустым:
 * у {@link #ARCHIMATE} и {@link #GIT_YAML} — да (round-trip), у остальных он
 * заполняется по факту.
 */
public enum ExportFormat {
    ARCHIMATE(true, InterchangeMessages.FORMAT_ARCHIMATE),
    OPEN_EXCHANGE(false, InterchangeMessages.FORMAT_OPEN_EXCHANGE),
    CSV_CATALOG(false, InterchangeMessages.FORMAT_CSV_CATALOG),
    SVG(false, InterchangeMessages.FORMAT_SVG),
    PNG(false, InterchangeMessages.FORMAT_PNG),
    GIT_YAML(true, InterchangeMessages.FORMAT_GIT_YAML);

    private final boolean lossless;
    private final String labelKey;

    ExportFormat(boolean lossless, String labelKey) {
        this.lossless = lossless;
        this.labelKey = labelKey;
    }

    /** Название формата для текста пользователю — переводится на границе. */
    public Message label() {
        return Message.of(labelKey);
    }

    /** Формат обещает round-trip без потерь: непустой отчёт для него — дефект, а не результат. */
    public boolean lossless() {
        return lossless;
    }
}
