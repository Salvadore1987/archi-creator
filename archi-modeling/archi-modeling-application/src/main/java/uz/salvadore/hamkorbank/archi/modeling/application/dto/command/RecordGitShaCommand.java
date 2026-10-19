package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.UUID;

/** Связать версию с коммитом, в котором её содержимое лежит в репозитории. */
public record RecordGitShaCommand(UUID modelId, long versionNo, String sha) {
}
