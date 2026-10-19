package uz.salvadore.hamkorbank.archi.advisor.application.access;

import java.util.EnumSet;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Role;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;

/**
 * Операции помощника и роли, которым они доступны. Имя в скобках — имя use case'а:
 * по нему же метрики и аудит. Матчеры URL роли не проверяют — проверяет эта таблица.
 */
public enum AdvisorOperation {
    REVIEW_MODEL("ReviewModel", AdvisorMessages.OPERATION_REVIEW_MODEL, Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    EXPLAIN_ELEMENT("ExplainElement", AdvisorMessages.OPERATION_EXPLAIN_ELEMENT,
            Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    PLAN_MODEL("PlanModel", AdvisorMessages.OPERATION_PLAN_MODEL, Role.ARCHITECT, Role.ADMIN),
    GENERATE_DOCUMENTATION("GenerateDocumentation", AdvisorMessages.OPERATION_GENERATE_DOCUMENTATION,
            Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    ADVISOR_STATUS("AdvisorStatus", AdvisorMessages.OPERATION_ADVISOR_STATUS, Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    TOGGLE_ADVISOR("ToggleAdvisor", AdvisorMessages.OPERATION_TOGGLE_ADVISOR, Role.ADMIN),
    SET_TOKEN_BUDGET("SetTokenBudget", AdvisorMessages.OPERATION_SET_TOKEN_BUDGET, Role.ADMIN),
    VIEW_TOKEN_USAGE("ViewTokenUsage", AdvisorMessages.OPERATION_VIEW_TOKEN_USAGE, Role.ADMIN);

    private final String useCase;
    private final String labelKey;
    private final Set<Role> roles;

    AdvisorOperation(String useCase, String labelKey, Role first, Role... rest) {
        this.useCase = useCase;
        this.labelKey = labelKey;
        this.roles = EnumSet.of(first, rest);
    }

    /** Операция функции помощника — по ней проверяется роль обращения. */
    public static AdvisorOperation of(AdvisorFunction function) {
        return switch (function) {
            case REVIEW -> REVIEW_MODEL;
            case EXPLAIN -> EXPLAIN_ELEMENT;
            case PLAN -> PLAN_MODEL;
            case DOCUMENT -> GENERATE_DOCUMENTATION;
        };
    }

    /** Имя use case'а — метка метрик и аудита. */
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
