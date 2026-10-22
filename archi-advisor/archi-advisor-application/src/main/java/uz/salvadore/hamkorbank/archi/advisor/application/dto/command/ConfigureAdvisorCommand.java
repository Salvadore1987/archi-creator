package uz.salvadore.hamkorbank.archi.advisor.application.dto.command;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Выключатель и месячный лимит пространства.
 *
 * @param workspaceId       {@code null} — пространство по умолчанию
 * @param monthlyTokenLimit {@code null} — не задан, отказ «поле не задано»
 */
public record ConfigureAdvisorCommand(@Nullable UUID workspaceId, boolean enabled,
                                      @Nullable Long monthlyTokenLimit) {
}
