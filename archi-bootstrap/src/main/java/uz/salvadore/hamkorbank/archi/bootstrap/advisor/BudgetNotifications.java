package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudgetExhausted;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudgetThresholdReached;

/**
 * Уведомление администратора о бюджете. Почты в стеке нет: канал — журнал уровня
 * {@code WARN} и метрика заполнения бюджета, по которой стоит алерт мониторинга.
 * Событие приходит один раз за пересечение порога, а не на каждое обращение.
 */
public final class BudgetNotifications {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetNotifications.class);

    @EventListener
    public void thresholdReached(TokenBudgetThresholdReached event) {
        LOG.warn("advisor budget threshold reached: workspace={} period={} consumed={} limit={} threshold={}%",
                event.workspaceId(), event.period(), event.consumedTokens(), event.limitTokens(),
                event.thresholdPercent());
    }

    @EventListener
    public void exhausted(TokenBudgetExhausted event) {
        LOG.warn("advisor budget exhausted: workspace={} period={} consumed={} limit={}",
                event.workspaceId(), event.period(), event.consumedTokens(), event.limitTokens());
    }
}
