package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Параметры каталога: разделитель — {@code ;} по умолчанию, {@code ,} для
 * стандартного CSV; {@code folderArchiId} — поддерево папки, {@code archi_id} которой
 * стабилен между версиями, в отличие от внутреннего ключа; {@code null} — вся модель.
 */
public record CatalogCsvOptions(char separator, String folderArchiId) {

    /**
     * Параметры из запроса: разделитель одним символом или словом {@code tab}, по умолчанию {@code ;};
     * пустая или не заданная ({@code null}) папка — вся модель. Недопустимый разделитель — отказ:
     * значения проверяются здесь, при разборе запроса, а не в конструкторе.
     */
    public static CatalogCsvOptions of(String separator, String folderArchiId) {
        String value = separator == null ? ";" : separator;
        if (value.length() != 1 && !value.equals("tab")) {
            throw new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE,
                    Message.of(InterchangeMessages.CSV_SEPARATOR_LENGTH), Map.of());
        }
        char separatorChar = value.equals("tab") ? '\t' : value.charAt(0);
        if (separatorChar != ';' && separatorChar != ',' && separatorChar != '\t') {
            throw new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE,
                    Message.of(InterchangeMessages.CSV_SEPARATOR), Map.of());
        }
        return new CatalogCsvOptions(separatorChar,
                folderArchiId == null || folderArchiId.isBlank() ? null : folderArchiId);
    }
}
