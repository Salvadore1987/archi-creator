package uz.salvadore.hamkorbank.archi.interchange.application.dto.command;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Применение коммита из отчёта: ссылка и коммит, отпечаток содержимого, комментарий версии.
 * Не переданное поле — {@code null}; что допустимо, решает сценарий.
 */
public record PullModelFromGitCommand(UUID modelId, @Nullable String ref, @Nullable String commitSha,
                                      @Nullable String baseHash, @Nullable String comment) {

    public PullModelFromGitCommand {
        Objects.requireNonNull(modelId, "modelId");
    }
}
