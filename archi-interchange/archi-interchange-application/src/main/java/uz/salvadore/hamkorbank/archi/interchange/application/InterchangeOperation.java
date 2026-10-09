package uz.salvadore.hamkorbank.archi.interchange.application;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Операции interchange и роли, которым они разрешены: проверка идёт по операции
 * сценария, а не по URL. Экспорт доступен {@code VIEWER} намеренно.
 */
public enum InterchangeOperation {
    IMPORT_ARCHIMATE_FILE("ImportArchimateFile", Role.ARCHITECT, Role.ADMIN),
    EXPORT_ARCHIMATE("ExportArchimate", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    EXPORT_CATALOG_CSV("ExportCatalogCsv", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    CONFIGURE_IMPORT_POLICY("ConfigureImportPolicy", Role.ADMIN);

    private final String useCase;
    private final Set<Role> roles;

    InterchangeOperation(String useCase, Role first, Role... rest) {
        this.useCase = useCase;
        this.roles = EnumSet.of(first, rest);
    }

    public String useCase() {
        return useCase;
    }

    public void require(EditorIdentity actor) {
        if (actor.roles().stream().noneMatch(roles::contains)) {
            throw new InterchangeException(InterchangeCodes.ACCESS_DENIED, Failure.FORBIDDEN,
                    Message.of(InterchangeMessages.OPERATION_DENIED, useCase, roles), Map.of("operation", useCase));
        }
    }
}
