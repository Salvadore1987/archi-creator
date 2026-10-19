package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence;

import jakarta.persistence.EntityManager;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AuditLog;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Разбивка расхода из записей аудита: по авторам и по функциям, отказы — отдельным
 * счётчиком. Месяц считается в том же часовом поясе, что и бюджет.
 */
@Repository
public class JpaAuditLog implements AuditLog {

    private final EntityManager em;

    private final ZoneId zone;

    public JpaAuditLog(EntityManager em, BudgetZone zone) {
        this.em = em;
        this.zone = zone.value();
    }

    @Override
    public List<UsageRow> byUser(WorkspaceId workspaceId, BudgetPeriod period) {
        return rows("user_subject", workspaceId, period);
    }

    @Override
    public List<UsageRow> byFunction(WorkspaceId workspaceId, BudgetPeriod period) {
        return rows("action", workspaceId, period);
    }

    private List<UsageRow> rows(String column, WorkspaceId workspaceId, BudgetPeriod period) {
        ZonedDateTime from = period.month().atDay(1).atStartOfDay(zone);
        ZonedDateTime to = period.resetsAt().atStartOfDay(zone);
        List<?> rows = em.createNativeQuery("select " + column + ", count(*), "
                        + "count(*) filter (where outcome = 'REJECTED'), coalesce(sum(prompt_tokens), 0), "
                        + "coalesce(sum(completion_tokens), 0) from ai_audit_log "
                        + "where workspace_id = :w and created_at >= :from and created_at < :to "
                        + "group by " + column + " order by " + column)
                .setParameter("w", workspaceId.value())
                .setParameter("from", from.toInstant()).setParameter("to", to.toInstant())
                .getResultList();
        return rows.stream().map(Object[].class::cast)
                .map(r -> new UsageRow((String) r[0], ((Number) r[1]).longValue(), ((Number) r[2]).longValue(),
                        ((Number) r[3]).longValue(), ((Number) r[4]).longValue()))
                .toList();
    }

    /** Часовой пояс учёта месяца — один на бюджет и на отчёт. */
    public record BudgetZone(ZoneId value) {
    }
}
