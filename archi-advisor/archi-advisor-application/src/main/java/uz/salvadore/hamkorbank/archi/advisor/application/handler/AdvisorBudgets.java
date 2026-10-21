package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import java.time.Instant;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSettingsRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository.BudgetPolicy;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.TokenBudgetId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Бюджет пространства, собранный с его настройками: выключатель и лимит, а лимит, не заданный
 * администратором, — значением по умолчанию. Методы зовутся внутри транзакции сценария.
 */
public final class AdvisorBudgets {

    private final AdvisorKernel kernel;
    private final AdvisorSettingsRepository settings;
    private final TokenBudgetRepository budgets;
    private final AdvisorPolicy policy;

    public AdvisorBudgets(AdvisorKernel kernel, AdvisorSettingsRepository settings, TokenBudgetRepository budgets,
                          AdvisorPolicy policy) {
        this.kernel = kernel;
        this.settings = settings;
        this.budgets = budgets;
        this.policy = policy;
    }

    /** Действующие выключатель и лимит пространства. */
    BudgetPolicy policyOf(WorkspaceId workspaceId) {
        AdvisorSettingsRepository.StoredSettings stored = settings.find(workspaceId);
        return new BudgetPolicy(stored.enabled(), Objects.requireNonNullElse(stored.monthlyTokenLimit(),
                policy.defaultMonthlyTokenLimit()));
    }

    /** Месяц бюджета, в который попадает момент. */
    BudgetPeriod period(Instant instant) {
        return BudgetPeriod.containing(instant, policy.budgetZone());
    }

    /** Бюджет месяца без блокировки; строки ещё нет — пустой бюджет с действующими настройками. */
    TokenBudget current(WorkspaceId workspaceId, BudgetPeriod period) {
        BudgetPolicy effective = policyOf(workspaceId);
        return budgets.find(workspaceId, period, effective).orElseGet(() -> TokenBudget.open(
                TokenBudgetId.next(kernel.uuids()), workspaceId, period, effective.limitTokens(), effective.enabled()));
    }

    /** Бюджет месяца под блокировкой строки до конца транзакции — для списания и смены лимита. */
    TokenBudget lock(WorkspaceId workspaceId, BudgetPeriod period, BudgetPolicy effective) {
        return budgets.lock(workspaceId, period, effective, TokenBudgetId.next(kernel.uuids()));
    }

    /** Записать бюджет. */
    void save(TokenBudget budget) {
        budgets.save(budget);
    }
}
