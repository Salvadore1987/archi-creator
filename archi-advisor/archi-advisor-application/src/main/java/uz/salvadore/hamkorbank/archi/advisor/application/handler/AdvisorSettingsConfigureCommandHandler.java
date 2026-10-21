package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import uz.salvadore.hamkorbank.archi.advisor.application.access.AccessPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AdvisorOperation;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ConfigureAdvisorCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorSettingsResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.mapper.AdvisorDataMapper;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSettingsRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository.BudgetPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.port.WorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Новый выключатель и лимит. Бюджет месяца пересчитывает состояние от нового лимита;
 * расход не меняется — поднять лимит можно, списать расход нельзя.
 */
public final class AdvisorSettingsConfigureCommandHandler {

    private final AdvisorKernel kernel;
    private final AdvisorBudgets budgets;
    private final AdvisorSettingsRepository settings;
    private final WorkspaceDirectory workspaces;

    public AdvisorSettingsConfigureCommandHandler(AdvisorKernel kernel, AdvisorBudgets budgets,
                                                  AdvisorSettingsRepository settings, WorkspaceDirectory workspaces) {
        this.kernel = kernel;
        this.budgets = budgets;
        this.settings = settings;
        this.workspaces = workspaces;
    }

    /** Записать настройки и пересчитать бюджет месяца; лимит не задан или не положителен — отказ. */
    public AdvisorSettingsResponse handle(Requester actor, ConfigureAdvisorCommand command) {
        return kernel.run(AdvisorOperation.SET_TOKEN_BUDGET, actor, () -> {
            AccessPolicy.require(actor, AdvisorOperation.TOGGLE_ADVISOR);
            if (command.monthlyTokenLimit() == null) {
                throw AdvisorDomainException.invalid(Message.of(AdvisorMessages.FIELD_REQUIRED, "monthlyTokenLimit"));
            }
            long limit = command.monthlyTokenLimit();
            if (limit <= 0) {
                throw AdvisorDomainException.invalid(Message.of(AdvisorMessages.LIMIT_NOT_POSITIVE));
            }
            boolean enabled = command.enabled();
            WorkspaceId workspaceId = workspaces.resolve(AdvisorDataMapper.workspace(command.workspaceId()));
            TokenBudget changed = kernel.unitOfWork().write(() -> {
                settings.save(workspaceId, enabled, limit);
                TokenBudget budget = budgets.lock(workspaceId, budgets.period(kernel.now()),
                        new BudgetPolicy(enabled, limit));
                budget.changeLimit(limit, kernel.now());
                budgets.save(budget);
                kernel.events().publish(budget.pullEvents());
                return budget;
            });
            kernel.metrics().budget(changed);
            return new AdvisorSettingsResponse(enabled, limit);
        });
    }
}
