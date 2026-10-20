package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

import java.util.UUID;

/**
 * Настройка импорта рабочего пространства.
 *
 * @param strictImport строгий режим: файл с нарушениями матрицы связей отклоняется
 */
public record ImportPolicyResponse(UUID workspaceId, boolean strictImport) {
}
