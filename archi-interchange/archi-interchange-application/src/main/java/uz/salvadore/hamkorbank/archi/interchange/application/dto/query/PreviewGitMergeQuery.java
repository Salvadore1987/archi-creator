package uz.salvadore.hamkorbank.archi.interchange.application.dto.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Отчёт слияния по ссылке с выборами по уже показанным конфликтам, ничего не меняет.
 *
 * @param ref         ветка, тег или коммит предложения; {@code null} — не задана
 * @param resolutions выбор по адресу конфликта: {@code OURS} или {@code THEIRS}
 */
public record PreviewGitMergeQuery(UUID modelId, String ref, Map<String, String> resolutions) {

    public PreviewGitMergeQuery {
        Objects.requireNonNull(modelId, "modelId");
        resolutions = resolutions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(resolutions));
    }
}
