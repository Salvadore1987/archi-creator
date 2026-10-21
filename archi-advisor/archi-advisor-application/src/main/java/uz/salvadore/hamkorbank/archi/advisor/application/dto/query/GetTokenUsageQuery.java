package uz.salvadore.hamkorbank.archi.advisor.application.dto.query;

import java.util.UUID;

/**
 * Расход токенов за месяц.
 *
 * @param workspaceId {@code null} — пространство по умолчанию
 * @param period      месяц {@code ГГГГ-ММ}; {@code null} — текущий
 */
public record GetTokenUsageQuery(UUID workspaceId, String period) {
}
