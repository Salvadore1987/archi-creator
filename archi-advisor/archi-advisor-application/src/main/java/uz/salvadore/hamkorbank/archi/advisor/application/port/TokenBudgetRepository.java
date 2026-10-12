package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.TokenBudgetId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Бюджеты периодов. В хранении — только расход: лимит и выключатель — настройки
 * пространства, и бюджет собирается с ними ({@link BudgetPolicy}). Списание идёт под
 * блокировкой строки бюджета — иначе две параллельные сессии теряют расход одна другой.
 */
public interface TokenBudgetRepository {

    /** Бюджет периода без блокировки; строки ещё нет — пусто. */
    Optional<TokenBudget> find(WorkspaceId workspaceId, BudgetPeriod period, BudgetPolicy policy);

    /**
     * Бюджет периода под блокировкой до конца транзакции. Строки нет — заводится
     * с ключом {@code newId}; параллельное заведение не даёт двух строк.
     */
    TokenBudget lock(WorkspaceId workspaceId, BudgetPeriod period, BudgetPolicy policy, TokenBudgetId newId);

    void save(TokenBudget budget);

    /** Действующие настройки: выключатель и лимит, уже с подставленным значением по умолчанию. */
    record BudgetPolicy(boolean enabled, long limitTokens) {
    }
}
