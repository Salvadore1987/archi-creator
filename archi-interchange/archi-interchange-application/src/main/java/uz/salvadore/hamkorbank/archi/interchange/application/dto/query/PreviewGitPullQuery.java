package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Отчёт об изменениях по ссылке, ничего не меняет.
 *
 * @param ref ветка, тег или коммит; {@code null} — не задана
 */
public record PreviewGitPullQuery(UUID modelId, @Nullable String ref) {

    public PreviewGitPullQuery {
        Objects.requireNonNull(modelId, "modelId");
    }
}
