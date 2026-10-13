package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.util.Objects;
import java.util.Optional;

/**
 * Запрос картинки как его подал клиент: формат по имени из контракта API
 * ({@code svg}, {@code png}), плотность, кривые вместо текста, фон и версия.
 * Что допустимо и что подставить вместо пропущенного, решает {@link ViewImageService}.
 */
public record ImageRequest(Optional<String> format, Optional<Integer> scale, Optional<Boolean> outline,
                           Optional<String> background, Optional<Long> versionNo) {

    public ImageRequest {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(outline, "outline");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(versionNo, "versionNo");
    }
}
