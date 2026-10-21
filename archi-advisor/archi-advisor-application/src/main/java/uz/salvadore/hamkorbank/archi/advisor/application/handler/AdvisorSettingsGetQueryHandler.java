package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import uz.salvadore.hamkorbank.archi.advisor.application.access.AdvisorOperation;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorSettingsQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorSettingsResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.mapper.AdvisorDataMapper;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository.BudgetPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.port.WorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Выключатель и действующий лимит пространства — для администратора. */
public final class AdvisorSettingsGetQueryHandler {

    private final AdvisorKernel kernel;
    private final AdvisorBudgets budgets;
    private final WorkspaceDirectory workspaces;

    public AdvisorSettingsGetQueryHandler(AdvisorKernel kernel, AdvisorBudgets budgets,
                                          WorkspaceDirectory workspaces) {
        this.kernel = kernel;
        this.budgets = budgets;
        this.workspaces = workspaces;
    }

    /** Действующие настройки: лимит, не заданный администратором, — значением по умолчанию. */
    public AdvisorSettingsResponse handle(Requester actor, GetAdvisorSettingsQuery query) {
        return kernel.run(AdvisorOperation.TOGGLE_ADVISOR, actor, () -> {
            WorkspaceId workspaceId = workspaces.resolve(AdvisorDataMapper.workspace(query.workspaceId()));
            BudgetPolicy effective = kernel.unitOfWork().read(() -> budgets.policyOf(workspaceId));
            return new AdvisorSettingsResponse(effective.enabled(), effective.limitTokens());
        });
    }
}
