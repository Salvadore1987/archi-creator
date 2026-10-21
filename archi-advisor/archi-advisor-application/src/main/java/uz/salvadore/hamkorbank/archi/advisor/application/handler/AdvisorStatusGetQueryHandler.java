package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import uz.salvadore.hamkorbank.archi.advisor.application.access.AdvisorOperation;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorStatusQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorStatusResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.mapper.AdvisorDataMapper;
import uz.salvadore.hamkorbank.archi.advisor.application.port.WorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Включён ли помощник и в каком состоянии бюджет месяца — по нему интерфейс показывает панель. */
public final class AdvisorStatusGetQueryHandler {

    private final AdvisorKernel kernel;
    private final AdvisorBudgets budgets;
    private final WorkspaceDirectory workspaces;

    public AdvisorStatusGetQueryHandler(AdvisorKernel kernel, AdvisorBudgets budgets, WorkspaceDirectory workspaces) {
        this.kernel = kernel;
        this.budgets = budgets;
        this.workspaces = workspaces;
    }

    /** Состояние помощника в пространстве. */
    public AdvisorStatusResponse handle(Requester actor, GetAdvisorStatusQuery query) {
        return kernel.run(AdvisorOperation.ADVISOR_STATUS, actor, () -> {
            WorkspaceId workspaceId = workspaces.resolve(AdvisorDataMapper.workspace(query.workspaceId()));
            BudgetPeriod period = budgets.period(kernel.now());
            TokenBudget budget = kernel.unitOfWork().read(() -> budgets.current(workspaceId, period));
            return new AdvisorStatusResponse(workspaceId.value(), budget.advisorEnabled(), budget.status().name(),
                    period.value(), period.resetsAt());
        });
    }
}
