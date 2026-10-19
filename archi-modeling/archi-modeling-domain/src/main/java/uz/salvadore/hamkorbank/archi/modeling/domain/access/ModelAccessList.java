package uz.salvadore.hamkorbank.archi.modeling.domain.access;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Список доступа к модели поверх ролей — агрегат.
 *
 * <p>Пустой — модель открыта по ролям. Непустой — только перечисленным и {@code ADMIN}.
 * Список сужает роль и не расширяет её: проверка роли идёт отдельно и раньше.
 */
public final class ModelAccessList extends AggregateRoot<ModelId> {

    /** Код отказа, когда список доступа не пускает пользователя к модели. */
    public static final String INVARIANT = ModelingCodes.ACCESS_LIST;

    private final List<AclEntry> entries;

    private ModelAccessList(ModelId modelId, List<AclEntry> entries) {
        Objects.requireNonNull(modelId, "modelId");
        entries = List.copyOf(new LinkedHashSet<>(entries));
        super(modelId);
        this.entries = entries;
    }

    /** Сборка из сохранённых значений — для хранилища; значения проверяются, как при создании. */
    public static ModelAccessList restore(ModelId modelId, List<AclEntry> entries) {
        return new ModelAccessList(modelId, entries);
    }

    /**
     * Новый список доступа модели взамен прежнего — целиком: команда описывает итог, а не правку.
     * Задаёт его только автор модели или {@code ADMIN}.
     */
    public static ModelAccessList replace(EditorIdentity editor, String modelCreatedBy, ModelId modelId,
                                          List<AclEntry> entries) {
        requireManager(editor, modelCreatedBy);
        return new ModelAccessList(modelId, entries);
    }

    /** Пустой список: модель открыта всем по ролям. */
    public static ModelAccessList open(ModelId modelId) {
        return new ModelAccessList(modelId, List.of());
    }

    /** Пускает ли список автора команды с уровнем {@code needed}; {@code ADMIN} проходит всегда. */
    public boolean permits(EditorIdentity editor, AclAccess needed) {
        return editor.isAdmin() || entries.isEmpty()
                || entries.stream().anyMatch(e -> e.names(editor) && e.access().covers(needed));
    }

    /**
     * Нет права — модель для пользователя не существует: {@code 404} с тем же кодом и текстом,
     * что у несуществующей модели, иначе отказ раскрыл бы то, что список скрывает. Исключение — тот, кто модель
     * видит, но правит без {@code WRITE}: ему честное {@code 403}.
     */
    public void require(EditorIdentity editor, AclAccess needed) {
        if (permits(editor, needed)) {
            return;
        }
        if (permits(editor, AclAccess.READ)) {
            throw new ModelingDomainException(INVARIANT, Failure.FORBIDDEN, Message.of(ModelingMessages.ACL_READ_ONLY));
        }
        throw ModelingDomainException.notFound(Message.of(ModelingMessages.MODEL, modelId()));
    }

    /** Список меняют {@code ADMIN} и автор модели. */
    public static void requireManager(EditorIdentity editor, String modelCreatedBy) {
        if (!editor.isAdmin() && !editor.subject().equals(modelCreatedBy)) {
            throw new ModelingDomainException(ModelingCodes.ACCESS_DENIED, Failure.FORBIDDEN,
                    Message.of(ModelingMessages.ACL_MANAGERS_ONLY));
        }
    }

    /** Модель, доступ к которой ограничивает список. */
    public ModelId modelId() {
        return id();
    }

    /** Записи списка: кто и с каким уровнем доступа. */
    public List<AclEntry> entries() {
        return entries;
    }
}
