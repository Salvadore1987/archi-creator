package uz.salvadore.hamkorbank.archi.modeling.application.access;

import java.util.EnumSet;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Операции modeling и роли, которым они доступны. Имя в скобках — имя use case'а:
 * по нему же метрики и аудит.
 *
 * <p>Таблица одна, и это она: матчеры URL роли не проверяют (SecurityConfig, этап 0).
 */
public enum Operation {
    OPEN_MODEL("OpenModel", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    OPEN_VIEW("OpenView", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    VALIDATE_MODEL("ValidateModel", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    LIST_VERSIONS("ListVersions", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    CREATE_MODEL("CreateModel", Role.ARCHITECT, Role.ADMIN),
    RENAME_MODEL("RenameModel", Role.ARCHITECT, Role.ADMIN),
    ACQUIRE_LOCK("AcquireLock", Role.ARCHITECT, Role.ADMIN),
    RELEASE_LOCK("ReleaseLock", Role.ARCHITECT, Role.ADMIN),
    CREATE_ELEMENT("CreateElement", Role.ARCHITECT, Role.ADMIN),
    UPDATE_ELEMENT("UpdateElement", Role.ARCHITECT, Role.ADMIN),
    DELETE_ELEMENT("DeleteElement", Role.ARCHITECT, Role.ADMIN),
    CREATE_RELATIONSHIP("CreateRelationship", Role.ARCHITECT, Role.ADMIN),
    UPDATE_RELATIONSHIP("UpdateRelationship", Role.ARCHITECT, Role.ADMIN),
    DELETE_RELATIONSHIP("DeleteRelationship", Role.ARCHITECT, Role.ADMIN),
    CREATE_VIEW("CreateView", Role.ARCHITECT, Role.ADMIN),
    SAVE_VIEW_LAYOUT("SaveViewLayout", Role.ARCHITECT, Role.ADMIN),
    REORGANIZE_TREE("ReorganizeTree", Role.ARCHITECT, Role.ADMIN),
    SAVE_MODEL("SaveModel", Role.ARCHITECT, Role.ADMIN),
    RESTORE_VERSION("RestoreVersion", Role.ARCHITECT, Role.ADMIN),
    LABEL_VERSION("LabelVersion", Role.ARCHITECT, Role.ADMIN),
    DELETE_MODEL("DeleteModel", Role.ARCHITECT, Role.ADMIN),
    RESTORE_MODEL("RestoreModel", Role.ADMIN),
    PURGE_MODEL("PurgeModel", Role.ADMIN),
    MANAGE_ACCESS("ManageModelAccess", Role.ARCHITECT, Role.ADMIN);

    private final String useCase;
    private final Set<Role> roles;

    Operation(String useCase, Role first, Role... rest) {
        this.useCase = useCase;
        this.roles = EnumSet.of(first, rest);
    }

    public String useCase() {
        return useCase;
    }

    public Set<Role> roles() {
        return roles;
    }
}
