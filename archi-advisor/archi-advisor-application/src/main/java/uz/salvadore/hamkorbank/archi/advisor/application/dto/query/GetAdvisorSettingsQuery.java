package uz.salvadore.hamkorbank.archi.advisor.application.dto.query;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Выключатель и действующий лимит пространства.
 *
 * @param workspaceId {@code null} — пространство по умолчанию
 */
public record GetAdvisorSettingsQuery(@Nullable UUID workspaceId) {
}
