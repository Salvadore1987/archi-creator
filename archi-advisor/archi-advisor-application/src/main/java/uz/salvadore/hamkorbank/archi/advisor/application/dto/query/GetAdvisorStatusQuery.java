package uz.salvadore.hamkorbank.archi.advisor.application.dto.query;

import java.util.UUID;

/**
 * Состояние помощника в пространстве.
 *
 * @param workspaceId {@code null} — пространство по умолчанию
 */
public record GetAdvisorStatusQuery(UUID workspaceId) {
}
