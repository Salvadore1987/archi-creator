package uz.salvadore.hamkorbank.archi.interchange.application.dto.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Смена настройки импорта рабочего пространства.
 *
 * @param strictImport строгий режим: файл с нарушениями матрицы связей отклоняется
 */
public record ConfigureImportPolicyCommand(UUID workspaceId, boolean strictImport) {

    public ConfigureImportPolicyCommand {
        Objects.requireNonNull(workspaceId, "workspaceId");
    }
}
