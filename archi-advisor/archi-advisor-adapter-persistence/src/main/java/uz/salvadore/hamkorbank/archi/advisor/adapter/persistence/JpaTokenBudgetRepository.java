package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.TokenBudgetRepository;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.TokenBudgetId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Бюджеты месяцев. Строка месяца заводится вставкой «если нет» и читается с
 * блокировкой {@code FOR UPDATE}: две сессии, завершившиеся одновременно, списывают
 * расход по очереди, и ни одно списание не теряется.
 */
@Repository
public class JpaTokenBudgetRepository implements TokenBudgetRepository {

    @PersistenceContext
    private EntityManager em;

    private final Clock clock;

    public JpaTokenBudgetRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<TokenBudget> find(WorkspaceId workspaceId, BudgetPeriod period, BudgetPolicy policy) {
        return query(workspaceId, period, LockModeType.NONE).map(row -> toDomain(row, policy));
    }

    @Override
    public TokenBudget lock(WorkspaceId workspaceId, BudgetPeriod period, BudgetPolicy policy, TokenBudgetId newId) {
        em.createNativeQuery("insert into token_budget (id, workspace_id, period, consumed_tokens, "
                        + "warning_threshold_percent, status, version, updated_at) "
                        + "values (:id, :w, :p, 0, :t, 'OK', 0, :now) on conflict (workspace_id, period) do nothing")
                .setParameter("id", newId.value()).setParameter("w", workspaceId.value())
                .setParameter("p", period.value()).setParameter("t", TokenBudget.DEFAULT_WARNING_PERCENT)
                .setParameter("now", clock.instant()).executeUpdate();
        return toDomain(query(workspaceId, period, LockModeType.PESSIMISTIC_WRITE).orElseThrow(), policy);
    }

    @Override
    public void save(TokenBudget budget) {
        TokenBudgetEntity row = em.find(TokenBudgetEntity.class, budget.id().value());
        row.consumedTokens = budget.consumedTokens();
        row.status = budget.status().name();
        row.version = budget.version() + 1;
        row.updatedAt = clock.instant();
        em.flush();
    }

    private Optional<TokenBudgetEntity> query(WorkspaceId workspaceId, BudgetPeriod period, LockModeType lock) {
        return em.createQuery("select b from TokenBudgetEntity b where b.workspaceId = :w and b.period = :p",
                        TokenBudgetEntity.class)
                .setParameter("w", workspaceId.value()).setParameter("p", period.value())
                .setLockMode(lock).getResultStream().findFirst();
    }

    private static TokenBudget toDomain(TokenBudgetEntity row, BudgetPolicy policy) {
        return TokenBudget.restore(TokenBudgetId.of(row.id), WorkspaceId.of(row.workspaceId),
                BudgetPeriod.parse(row.period), policy.limitTokens(), row.consumedTokens,
                row.warningThresholdPercent, policy.enabled(), row.version);
    }
}
