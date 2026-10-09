package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Строгость импорта — столбец {@code workspace.strict_import}. Строка общая с
 * modeling, а столбец — interchange: каждый контекст читает и пишет только своё.
 */
@Repository
public class NativeQueryImportPolicy implements ImportPolicy {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean strictImport(WorkspaceId workspaceId) {
        List<?> rows = em.createNativeQuery("select strict_import from workspace where id = :w")
                .setParameter("w", workspaceId.value()).getResultList();
        if (rows.isEmpty()) {
            throw notFound(workspaceId);
        }
        return (Boolean) rows.getFirst();
    }

    @Override
    public void setStrictImport(WorkspaceId workspaceId, boolean strict) {
        int updated = em.createNativeQuery("update workspace set strict_import = :s where id = :w")
                .setParameter("s", strict).setParameter("w", workspaceId.value()).executeUpdate();
        if (updated == 0) {
            throw notFound(workspaceId);
        }
    }

    private static InterchangeException notFound(WorkspaceId workspaceId) {
        return new InterchangeException("IXC_NOT_FOUND", Failure.NOT_FOUND,
                "рабочее пространство " + workspaceId + " не найдено", Map.of());
    }
}
