package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.util.List;
import java.util.Locale;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossEntry;

/**
 * Отчёт о потерях файлом {@code loss-report.json} рядом с выгрузкой. Записи — в порядке
 * обхода документа, тексты причин — на языке запроса; формат без зависимостей,
 * потому что в application-слое нет JSON-библиотеки.
 */
final class LossReportJson {

    private LossReportJson() {
    }

    /** {@code {"format":…,"sourceVersionNo":…,"entries":[…]}} с переводом строки в конце. */
    static String report(String format, long versionNo, List<LossEntry> entries) {
        StringBuilder out = new StringBuilder("{\"format\":").append(string(format)).append(",\"sourceVersionNo\":")
                .append(versionNo).append(",\"entries\":");
        entries(out, entries);
        return out.append("}\n").toString();
    }

    static void entries(StringBuilder out, List<LossEntry> entries) {
        out.append('[');
        for (int i = 0; i < entries.size(); i++) {
            LossEntry e = entries.get(i);
            out.append(i == 0 ? "" : ",").append("{\"archiId\":").append(string(e.archiId().value()))
                    .append(",\"objectKind\":").append(string(e.objectKind().value()))
                    .append(",\"lostAspect\":").append(string(e.lostAspect()))
                    .append(",\"reason\":").append(string(e.reason())).append('}');
        }
        out.append(']');
    }

    static String string(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
