package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Objects;
import java.util.UUID;

/**
 * Картинка представления — как её запросил клиент. Что допустимо и что подставить вместо
 * пропущенного ({@code null}), решает сценарий.
 *
 * @param format     {@code svg} или {@code png}
 * @param scale      плотность PNG
 * @param outline    подписи кривыми вместо текста
 * @param background {@code paper} или {@code transparent}
 * @param versionNo  номер версии; {@code null} — последняя
 */
public record ExportViewImageQuery(UUID viewId, String format, Integer scale, Boolean outline, String background,
                                   Long versionNo) {

    public ExportViewImageQuery {
        Objects.requireNonNull(viewId, "viewId");
    }
}
