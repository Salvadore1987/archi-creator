package uz.salvadore.hamkorbank.archi.interchange.application.exporting;


/**
 * Запрос картинки как его подал клиент: формат по имени из контракта API
 * ({@code svg}, {@code png}), плотность, кривые вместо текста, фон и версия.
 * Что допустимо и что подставить вместо пропущенного ({@code null}), решает {@link ViewImageService}.
 */
public record ImageRequest(String format, Integer scale, Boolean outline, String background, Long versionNo) {
}
