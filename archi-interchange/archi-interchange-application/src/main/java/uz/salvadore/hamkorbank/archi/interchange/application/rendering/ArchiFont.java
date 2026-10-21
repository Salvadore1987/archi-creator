package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.util.Optional;

/**
 * Шрифт Archi строкой {@code FontData} SWT: {@code 1|Segoe UI|10.0|1|WINDOWS|…} — версия,
 * семейство, кегль в пунктах, начертание битами (1 — полужирный, 2 — курсив). Начертания
 * нет — обычное. Один разбор для картинки и для Open Exchange Format: шрифт, который
 * принят в одном, принят и в другом.
 */
public record ArchiFont(String family, double sizePt, boolean bold, boolean italic) {

    /** Шрифт из строки файла; семейства или кегля нет, число не разбирается — пусто. */
    public static Optional<ArchiFont> parse(String value) {
        String[] parts = value.split("\\|");
        if (parts.length < 3 || parts[1].isBlank()) {
            return Optional.empty();
        }
        try {
            double size = Double.parseDouble(parts[2].strip());
            int flags = parts.length > 3 && !parts[3].isBlank() ? Integer.parseInt(parts[3].strip()) : 0;
            return Optional.of(new ArchiFont(parts[1], size, (flags & 1) == 1, (flags & 2) == 2));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }
}
