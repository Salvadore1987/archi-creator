package uz.salvadore.hamkorbank.archi.modeling.application.dto.query;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Какое пространство взять для команды.
 *
 * @param workspaceId названное клиентом; {@code null} — не назвал, берётся первое
 */
public record ResolveWorkspaceQuery(@Nullable UUID workspaceId) {
}
