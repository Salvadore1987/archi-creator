package uz.salvadore.hamkorbank.archi.advisor.application.access;

import java.util.EnumSet;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Role;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;

/**
 * Операции помощника и роли, которым они доступны. Имя в скобках — имя use case'а:
 * по нему же метрики и аудит. Матчеры URL роли не проверяют — проверяет эта таблица.
 */
public enum AdvisorOperation {
    REVIEW_MODEL("ReviewModel", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    EXPLAIN_ELEMENT("ExplainElement", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    PLAN_MODEL("PlanModel", Role.ARCHITECT, Role.ADMIN),
    GENERATE_DOCUMENTATION("GenerateDocumentation", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    ADVISOR_STATUS("AdvisorStatus", Role.VIEWER, Role.ARCHITECT, Role.ADMIN),
    TOGGLE_ADVISOR("ToggleAdvisor", Role.ADMIN),
    SET_TOKEN_BUDGET("SetTokenBudget", Role.ADMIN),
    VIEW_TOKEN_USAGE("ViewTokenUsage", Role.ADMIN);

    private final String useCase;
    private final Set<Role> roles;

    AdvisorOperation(String useCase, Role first, Role... rest) {
        this.useCase = useCase;
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

    public String useCase() {
        return useCase;
    }

    public Set<Role> roles() {
        return roles;
    }
}
