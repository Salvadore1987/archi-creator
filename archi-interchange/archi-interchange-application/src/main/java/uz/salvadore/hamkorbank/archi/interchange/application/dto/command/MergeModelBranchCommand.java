package uz.salvadore.hamkorbank.archi.interchange.application.dto.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Применение слияния из отчёта: ссылка, оба коммита, отпечаток содержимого, выборы и комментарий.
 * Не переданное поле — {@code null}; выбор без значения сохраняется — отказывает в нём сценарий.
 *
 * @param resolutions выбор по адресу конфликта: {@code OURS} или {@code THEIRS}
 */
public record MergeModelBranchCommand(UUID modelId, @Nullable String ref, @Nullable String commitSha,
                                      @Nullable String mergeBaseSha, @Nullable String baseHash,
                                      Map<String, String> resolutions, @Nullable String comment) {

    public MergeModelBranchCommand {
        Objects.requireNonNull(modelId, "modelId");
        resolutions = resolutions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(resolutions));
    }
}
