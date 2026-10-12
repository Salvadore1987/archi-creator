package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.util.List;

/**
 * Отчёт о потерях выгрузки. Пустой список — выгрузка без потерь, и это валидный
 * отчёт; отсутствие отчёта валидным не является.
 *
 * @param complete отчёт перечисляет всё, что не перенеслось, а не первые N позиций
 */
public record LossReport(List<LossEntry> entries, boolean complete) {

    public LossReport {
        entries = List.copyOf(entries);
    }

    public static LossReport lossless() {
        return new LossReport(List.of(), true);
    }

    public boolean empty() {
        return entries.isEmpty();
    }
}
