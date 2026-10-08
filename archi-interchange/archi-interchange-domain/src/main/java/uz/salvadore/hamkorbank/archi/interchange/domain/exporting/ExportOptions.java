package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Параметры выгрузки. Каждый относится к своим форматам: масштаб, контур и фон —
 * к SVG/PNG, разделитель и фильтр папок — к CSV.
 */
public record ExportOptions(Optional<BigDecimal> scale, Optional<Boolean> outline, Optional<String> background,
                            Optional<String> separator, Optional<String> folderFilter) {

    public ExportOptions {
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(outline, "outline");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(separator, "separator");
        Objects.requireNonNull(folderFilter, "folderFilter");
        if (scale.filter(s -> s.signum() <= 0).isPresent()) {
            throw new IllegalArgumentException("масштаб обязан быть положительным");
        }
    }

    public static ExportOptions none() {
        return new ExportOptions(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
