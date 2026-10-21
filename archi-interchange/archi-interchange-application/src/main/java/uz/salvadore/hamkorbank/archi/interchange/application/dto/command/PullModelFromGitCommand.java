package uz.salvadore.hamkorbank.archi.interchange.application.dto.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Применение коммита из отчёта: ссылка и коммит, отпечаток содержимого, комментарий версии.
 * Не переданное поле — {@code null}; что допустимо, решает сценарий.
 */
public record PullModelFromGitCommand(UUID modelId, String ref, String commitSha, String baseHash, String comment) {

    public PullModelFromGitCommand {
        Objects.requireNonNull(modelId, "modelId");
    }
}
