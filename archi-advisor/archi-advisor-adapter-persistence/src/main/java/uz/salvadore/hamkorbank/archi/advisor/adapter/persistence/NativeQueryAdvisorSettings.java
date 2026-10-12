package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSettingsRepository;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Выключатель и лимит — столбцы {@code workspace.ai_enabled} и
 * {@code workspace.ai_monthly_token_limit}. Строка общая с modeling, а столбцы —
 * помощника: каждый контекст читает и пишет только своё.
 */
@Repository
public class NativeQueryAdvisorSettings implements AdvisorSettingsRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public StoredSettings find(WorkspaceId workspaceId) {
        List<?> rows = em.createNativeQuery("select ai_enabled, ai_monthly_token_limit from workspace where id = :w")
                .setParameter("w", workspaceId.value()).getResultList();
        if (rows.isEmpty()) {
            throw notFound(workspaceId);
        }
        Object[] row = (Object[]) rows.getFirst();
        return new StoredSettings((Boolean) row[0], Optional.ofNullable((Number) row[1]).map(Number::longValue));
    }

    @Override
    public void save(WorkspaceId workspaceId, boolean enabled, long monthlyTokenLimit) {
        int updated = em.createNativeQuery(
                        "update workspace set ai_enabled = :e, ai_monthly_token_limit = :l where id = :w")
                .setParameter("e", enabled).setParameter("l", monthlyTokenLimit).setParameter("w", workspaceId.value())
                .executeUpdate();
        if (updated == 0) {
            throw notFound(workspaceId);
        }
    }

    private static AdvisorException notFound(WorkspaceId workspaceId) {
        return AdvisorException.notFound(Message.of(AdvisorMessages.WORKSPACE, workspaceId));
    }
}
