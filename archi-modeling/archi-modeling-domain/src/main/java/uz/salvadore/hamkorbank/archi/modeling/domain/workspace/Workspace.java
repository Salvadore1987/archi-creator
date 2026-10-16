package uz.salvadore.hamkorbank.archi.modeling.domain.workspace;

import java.time.Instant;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Рабочее пространство — арендатор и граница видимости моделей. Политики других
 * контекстов (строгость импорта, бюджет ИИ, Git) здесь не живут: ключ тот же,
 * хозяева — interchange и advisor.
 */
public final class Workspace extends AggregateRoot<WorkspaceId> {

    /** Предел длины имени пространства. */
    public static final int NAME_MAX = 200;

    private final String name;
    private final Instant createdAt;

    private Workspace(WorkspaceId id, String name, Instant createdAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name == null || name.isBlank() || name.length() > NAME_MAX) {
            throw new InvalidValueException(ModelingMessages.WORKSPACE_NAME_INVALID, NAME_MAX);
        }
        super(id);
        this.name = name;
        this.createdAt = createdAt;
    }

    /** Сборка из сохранённых значений — для хранилища; значения проверяются, как при создании. */
    public static Workspace restore(WorkspaceId id, String name, Instant createdAt) {
        return new Workspace(id, name, createdAt);
    }

    /** Название пространства. */
    public String name() {
        return name;
    }

    /** Момент создания пространства. */
    public Instant createdAt() {
        return createdAt;
    }
}
