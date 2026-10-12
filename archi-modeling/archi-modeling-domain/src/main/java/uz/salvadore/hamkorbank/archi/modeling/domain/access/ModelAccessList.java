package uz.salvadore.hamkorbank.archi.modeling.domain.access;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Список доступа к модели поверх ролей — агрегат.
 *
 * <p>Пустой — модель открыта по ролям. Непустой — только перечисленным и {@code ADMIN}.
 * Список сужает роль и не расширяет её: проверка роли идёт отдельно и раньше.
 */
public record ModelAccessList(ModelId modelId, List<AclEntry> entries) {

    public static final String INVARIANT = ModelingCodes.ACCESS_LIST;

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
            throw new ModelingException(INVARIANT, Failure.FORBIDDEN, Message.of(ModelingMessages.ACL_READ_ONLY));
        }
        throw new ModelingException(INVARIANT, Failure.NOT_FOUND,
                Message.of(ModelingMessages.NOT_FOUND, Message.of(ModelingMessages.MODEL, modelId)));
    }

    /** Список меняют {@code ADMIN} и автор модели. */
    public static void requireManager(EditorIdentity editor, String modelCreatedBy) {
        if (!editor.isAdmin() && !editor.subject().equals(modelCreatedBy)) {
            throw new ModelingException(ModelingCodes.ACCESS_DENIED, Failure.FORBIDDEN,
                    Message.of(ModelingMessages.ACL_MANAGERS_ONLY));
        }
    }
}
