package uz.salvadore.hamkorbank.archi.advisor.application.dto.query;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Состояние помощника в пространстве.
 *
 * @param workspaceId {@code null} — пространство по умолчанию
 */
public record GetAdvisorStatusQuery(@Nullable UUID workspaceId) {
}
