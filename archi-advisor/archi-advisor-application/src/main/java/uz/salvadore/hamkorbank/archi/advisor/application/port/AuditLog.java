package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.List;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Аудит обращений — источник разбивки расхода для администратора. */
public interface AuditLog {

    /** Расход периода по авторам. */
    List<UsageRow> byUser(WorkspaceId workspaceId, BudgetPeriod period);

    /** Расход периода по функциям помощника. */
    List<UsageRow> byFunction(WorkspaceId workspaceId, BudgetPeriod period);

    /**
     * @param key      {@code subject} автора или имя функции
     * @param rejected из них отклонено до вызова внешнего API
     */
    record UsageRow(String key, long requests, long rejected, long promptTokens, long completionTokens) {
    }
}
