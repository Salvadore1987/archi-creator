package uz.salvadore.hamkorbank.archi.modeling.application.access;

import java.util.EnumSet;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Операции modeling и роли, которым они доступны. Имя в скобках — имя use case'а:
 * по нему же метрики и аудит.
 *
 * <p>Таблица одна, и это она: матчеры URL в {@code SecurityConfig} роли не проверяют.
 */
public enum Operation {
    OPEN_MODEL("OpenModel", ModelingMessages.OPERATION_OPEN_MODEL, Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    OPEN_VIEW("OpenView", ModelingMessages.OPERATION_OPEN_VIEW, Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    VALIDATE_MODEL("ValidateModel", ModelingMessages.OPERATION_VALIDATE_MODEL, Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    LIST_VERSIONS("ListVersions", ModelingMessages.OPERATION_LIST_VERSIONS, Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    CREATE_MODEL("CreateModel", ModelingMessages.OPERATION_CREATE_MODEL, Role.ARCHITECT, Role.ADMIN),
    RENAME_MODEL("RenameModel", ModelingMessages.OPERATION_RENAME_MODEL, Role.ARCHITECT, Role.ADMIN),
    ACQUIRE_LOCK("AcquireLock", ModelingMessages.OPERATION_ACQUIRE_LOCK, Role.ARCHITECT, Role.ADMIN),
    RELEASE_LOCK("ReleaseLock", ModelingMessages.OPERATION_RELEASE_LOCK, Role.ARCHITECT, Role.ADMIN),
    CREATE_ELEMENT("CreateElement", ModelingMessages.OPERATION_CREATE_ELEMENT, Role.ARCHITECT, Role.ADMIN),
    UPDATE_ELEMENT("UpdateElement", ModelingMessages.OPERATION_UPDATE_ELEMENT, Role.ARCHITECT, Role.ADMIN),
    DELETE_ELEMENT("DeleteElement", ModelingMessages.OPERATION_DELETE_ELEMENT, Role.ARCHITECT, Role.ADMIN),
    CREATE_RELATIONSHIP("CreateRelationship", ModelingMessages.OPERATION_CREATE_RELATIONSHIP,
            Role.ARCHITECT, Role.ADMIN),
    UPDATE_RELATIONSHIP("UpdateRelationship", ModelingMessages.OPERATION_UPDATE_RELATIONSHIP,
            Role.ARCHITECT, Role.ADMIN),
    DELETE_RELATIONSHIP("DeleteRelationship", ModelingMessages.OPERATION_DELETE_RELATIONSHIP,
            Role.ARCHITECT, Role.ADMIN),
    CREATE_VIEW("CreateView", ModelingMessages.OPERATION_CREATE_VIEW, Role.ARCHITECT, Role.ADMIN),
    SAVE_VIEW_LAYOUT("SaveViewLayout", ModelingMessages.OPERATION_SAVE_VIEW_LAYOUT, Role.ARCHITECT, Role.ADMIN),
    AUTO_LAYOUT_VIEW("AutoLayoutView", ModelingMessages.OPERATION_AUTO_LAYOUT_VIEW, Role.ARCHITECT, Role.ADMIN),
    REORGANIZE_TREE("ReorganizeTree", ModelingMessages.OPERATION_REORGANIZE_TREE, Role.ARCHITECT, Role.ADMIN),
    SAVE_MODEL("SaveModel", ModelingMessages.OPERATION_SAVE_MODEL, Role.ARCHITECT, Role.ADMIN),
    RESTORE_VERSION("RestoreVersion", ModelingMessages.OPERATION_RESTORE_VERSION, Role.ARCHITECT, Role.ADMIN),
    LABEL_VERSION("LabelVersion", ModelingMessages.OPERATION_LABEL_VERSION, Role.ARCHITECT, Role.ADMIN),
    DELETE_MODEL("DeleteModel", ModelingMessages.OPERATION_DELETE_MODEL, Role.ARCHITECT, Role.ADMIN),
    RESTORE_MODEL("RestoreModel", ModelingMessages.OPERATION_RESTORE_MODEL, Role.ADMIN),
    PURGE_MODEL("PurgeModel", ModelingMessages.OPERATION_PURGE_MODEL, Role.ADMIN),
    MANAGE_ACCESS("ManageModelAccess", ModelingMessages.OPERATION_MANAGE_MODEL_ACCESS, Role.ARCHITECT, Role.ADMIN);

    private final String useCase;
    private final String labelKey;
    private final Set<Role> roles;

    Operation(String useCase, String labelKey, Role first, Role... rest) {
        this.useCase = useCase;
        this.labelKey = labelKey;
        this.roles = EnumSet.of(first, rest);
    }

    /** Имя сценария — метка метрик и аудита. */
    public String useCase() {
        return useCase;
    }

    /** Название операции для человека — сообщением, переводится вместе с фразой отказа. */
    public Message label() {
        return Message.of(labelKey);
    }

    /** Роли, которым операция доступна. */
    public Set<Role> roles() {
        return roles;
    }
}
