package uz.salvadore.hamkorbank.archi.modeling.domain.workspace;

import java.time.Instant;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Names;

/**
 * Рабочее пространство — арендатор и граница видимости моделей. Политики других
 * контекстов (строгость импорта, бюджет ИИ, Git) здесь не живут: ключ тот же,
 * хозяева — interchange и advisor.
 */
public record Workspace(WorkspaceId id, String name, Instant createdAt) {

    public Workspace {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("имя рабочего пространства от 1 до 200 символов");
        }
        Names.limited(name, "рабочего пространства");
    }
}
