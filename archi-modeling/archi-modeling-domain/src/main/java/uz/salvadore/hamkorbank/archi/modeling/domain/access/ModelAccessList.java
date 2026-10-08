package uz.salvadore.hamkorbank.archi.modeling.domain.access;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Список доступа к модели поверх ролей — агрегат (INV-MDL-011, FR-29).
 *
 * <p>Пустой — модель открыта по ролям. Непустой — только перечисленным и {@code ADMIN}.
 * Список сужает роль и не расширяет её: проверка роли идёт отдельно и раньше.
 */
public record ModelAccessList(ModelId modelId, List<AclEntry> entries) {

    public static final String INVARIANT = "INV-MDL-011";

    public ModelAccessList {
        Objects.requireNonNull(modelId, "modelId");
        entries = List.copyOf(new LinkedHashSet<>(entries));
    }

    public static ModelAccessList open(ModelId modelId) {
        return new ModelAccessList(modelId, List.of());
    }

    public boolean permits(EditorIdentity editor, AclAccess needed) {
        return editor.isAdmin() || entries.isEmpty()
                || entries.stream().anyMatch(e -> e.names(editor) && e.access().covers(needed));
    }

    /**
     * Нет права — модель для пользователя не существует: {@code 404}, а не {@code 403},
     * иначе отказ раскрыл бы то, что список скрывает. Исключение — тот, кто модель
     * видит, но правит без {@code WRITE}: ему честное {@code 403}.
     */
    public void require(EditorIdentity editor, AclAccess needed) {
        if (permits(editor, needed)) {
            return;
        }
        if (permits(editor, AclAccess.READ)) {
            throw new ModelingException(INVARIANT, Failure.FORBIDDEN,
                    "список доступа модели даёт только чтение");
        }
        throw new ModelingException(INVARIANT, Failure.NOT_FOUND, "модель " + modelId + " не найдена");
    }

    /** Список меняют {@code ADMIN} и автор модели (UC-MDL-007). */
    public static void requireManager(EditorIdentity editor, String modelCreatedBy) {
        if (!editor.isAdmin() && !editor.subject().equals(modelCreatedBy)) {
            throw new ModelingException(ModelingException.Codes.ACCESS_DENIED, Failure.FORBIDDEN,
                    "список доступа меняют ADMIN и автор модели");
        }
    }
}
