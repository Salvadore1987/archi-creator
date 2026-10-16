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
    IMPORT_ARCHIMATE_FILE("ImportArchimateFile", InterchangeMessages.OPERATION_IMPORT_ARCHIMATE_FILE,
            Role.ARCHITECT, Role.ADMIN),
    EXPORT_ARCHIMATE("ExportArchimate", InterchangeMessages.OPERATION_EXPORT_ARCHIMATE,
            Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    EXPORT_OPEN_EXCHANGE("ExportOpenExchange", InterchangeMessages.OPERATION_EXPORT_OPEN_EXCHANGE,
            Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    EXPORT_CATALOG_CSV("ExportCatalogCsv", InterchangeMessages.OPERATION_EXPORT_CATALOG_CSV,
            Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    EXPORT_VIEW_IMAGE("ExportViewImage", InterchangeMessages.OPERATION_EXPORT_VIEW_IMAGE,
            Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    EXPORT_ALL_VIEW_IMAGES("ExportAllViewImages", InterchangeMessages.OPERATION_EXPORT_ALL_VIEW_IMAGES,
            Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    CONFIGURE_IMPORT_POLICY("ConfigureImportPolicy", InterchangeMessages.OPERATION_CONFIGURE_IMPORT_POLICY, Role.ADMIN),
    PUSH_MODEL_TO_GIT("PushModelToGit", InterchangeMessages.OPERATION_PUSH_MODEL_TO_GIT, Role.ARCHITECT, Role.ADMIN),
    PULL_MODEL_FROM_GIT("PullModelFromGit", InterchangeMessages.OPERATION_PULL_MODEL_FROM_GIT,
            Role.ARCHITECT, Role.ADMIN),
    MERGE_MODEL_BRANCH("MergeModelBranch", InterchangeMessages.OPERATION_MERGE_MODEL_BRANCH,
            Role.ARCHITECT, Role.ADMIN),
    CONFIGURE_GIT_BINDING("ConfigureGitBinding", InterchangeMessages.OPERATION_CONFIGURE_GIT_BINDING, Role.ADMIN);

    private final String useCase;
    private final String labelKey;
    private final Set<Role> roles;

    InterchangeOperation(String useCase, String labelKey, Role first, Role... rest) {
        this.useCase = useCase;
        this.labelKey = labelKey;
        this.roles = EnumSet.of(first, rest);
    }

    /** Имя сценария — для метрик и сообщения об отказе. */
    public String useCase() {
        return useCase;
    }

    /** Название операции для человека — сообщением, переводится вместе с фразой отказа. */
    public Message label() {
        return Message.of(labelKey);
    }

    /** Отказывает с запретом доступа, если ни одна роль актора не допускает операцию. */
    public void require(EditorIdentity actor) {
        if (actor.roles().stream().noneMatch(roles::contains)) {
            throw new InterchangeException(InterchangeCodes.ACCESS_DENIED, Failure.FORBIDDEN,
                    Message.of(InterchangeMessages.OPERATION_DENIED, label()), Map.of("operation", useCase));
        }
    }
}
