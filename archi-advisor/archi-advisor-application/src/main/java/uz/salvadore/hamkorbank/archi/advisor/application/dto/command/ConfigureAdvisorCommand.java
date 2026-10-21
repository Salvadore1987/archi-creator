package uz.salvadore.hamkorbank.archi.advisor.application.dto.command;

import java.util.UUID;

/**
 * Выключатель и месячный лимит пространства.
 *
 * @param workspaceId       {@code null} — пространство по умолчанию
 * @param monthlyTokenLimit {@code null} — не задан, отказ «поле не задано»
 */
public record ConfigureAdvisorCommand(UUID workspaceId, boolean enabled, Long monthlyTokenLimit) {
}
