package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/**
 * Параметры выгрузки. Каждый относится к своим форматам: масштаб, контур и фон —
 * к SVG/PNG, разделитель и фильтр папок — к CSV. {@code null} — параметр не задан.
 */
public record ExportOptions(@Nullable BigDecimal scale, @Nullable Boolean outline, @Nullable String background,
                            @Nullable String separator, @Nullable String folderFilter) {

    public ExportOptions {
        if (scale != null && scale.signum() <= 0) {
            throw new InvalidValueException(InterchangeMessages.EXPORT_SCALE_POSITIVE);
        }
    }

    /** Параметры по умолчанию: ни один не задан. */
    public static ExportOptions none() {
        return new ExportOptions(null, null, null, null, null);
    }
}
