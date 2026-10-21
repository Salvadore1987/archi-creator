package uz.salvadore.hamkorbank.archi.advisor.application.dto.query;

import java.util.UUID;

/**
 * Выключатель и действующий лимит пространства.
 *
 * @param workspaceId {@code null} — пространство по умолчанию
 */
public record GetAdvisorSettingsQuery(UUID workspaceId) {
}
