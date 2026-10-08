package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.util.Map;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Параметры каталога: разделитель — {@code ;} по умолчанию, {@code ,} для
 * стандартного CSV; {@code folderArchiId} — поддерево папки, {@code archi_id} которой
 * стабилен между версиями, в отличие от внутреннего ключа.
 */
public record CatalogCsvOptions(char separator, Optional<String> folderArchiId) {

    public CatalogCsvOptions {
        if (separator != ';' && separator != ',' && separator != '\t') {
            throw new InterchangeException("IXC_INVALID_REQUEST", Failure.UNPROCESSABLE,
                    "разделитель CSV — ';', ',' или табуляция", Map.of());
        }
    }

    public static CatalogCsvOptions of(Optional<String> separator, Optional<String> folderArchiId) {
        String value = separator.orElse(";");
        if (value.length() != 1 && !value.equals("tab")) {
            throw new InterchangeException("IXC_INVALID_REQUEST", Failure.UNPROCESSABLE,
                    "разделитель CSV — один символ", Map.of());
        }
        return new CatalogCsvOptions(value.equals("tab") ? '\t' : value.charAt(0),
                folderArchiId.filter(f -> !f.isBlank()));
    }
}
