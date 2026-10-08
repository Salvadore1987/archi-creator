package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportRequest;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.Severity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Сессии импорта (INV-IXC-002, INV-IXC-003). Документ не хранится — только статус,
 * отчёт и результат: повтор по ключу отдаёт их, а не применяет файл снова.
 */
@Repository
public class JpaImportSessionStore implements ImportSessionRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<ImportSession> findByIdempotencyKey(WorkspaceId workspaceId, String idempotencyKey) {
        return em.createQuery("select s from ImportSessionEntity s where s.workspaceId = :w and s.idempotencyKey = :k",
                        ImportSessionEntity.class)
                .setParameter("w", workspaceId.value()).setParameter("k", idempotencyKey)
                .getResultStream().findFirst().map(this::toDomain);
    }

    @Override
    public Optional<ImportSession> find(ImportSessionId id) {
        return Optional.ofNullable(em.find(ImportSessionEntity.class, id.value())).map(this::toDomain);
    }

    @Override
    public void save(ImportSession session) {
        ImportSessionEntity entity = em.find(ImportSessionEntity.class, session.id().value());
        boolean fresh = entity == null;
        if (fresh) {
            entity = new ImportSessionEntity();
            entity.id = session.id().value();
        }
        entity.workspaceId = session.workspaceId().value();
        entity.targetModelId = session.targetModelId().map(ModelId::value).orElse(null);
        entity.sourceName = session.sourceName();
        entity.sourceHash = session.sourceHash().value();
        entity.sourceSize = session.sourceSize();
        entity.idempotencyKey = session.idempotencyKey();
        entity.strictMode = session.strictMode();
        entity.status = session.status().name();
        entity.startedBy = session.startedBy();
        entity.startedAt = session.startedAt();
        entity.finishedAt = session.finishedAt().orElse(null);
        entity.modelId = session.appliedModelId().map(ModelId::value).orElse(null);
        entity.versionNo = session.appliedVersionNo() > 0 ? session.appliedVersionNo() : null;
        try {
            if (fresh) {
                em.persist(entity);
            }
            em.createQuery("delete from ImportFindingEntity f where f.sessionId = :s")
                    .setParameter("s", session.id().value()).executeUpdate();
            List<ImportFinding> findings = session.findings();
            for (int i = 0; i < findings.size(); i++) {
                ImportFinding finding = findings.get(i);
                ImportFindingEntity row = new ImportFindingEntity();
                row.id = finding.id().value();
                row.sessionId = session.id().value();
                row.ordinal = i;
                row.severity = finding.severity().name();
                row.code = finding.code();
                row.message = finding.message();
                row.archiId = finding.archiId().map(ArchiId::value).orElse(null);
                row.xmlLine = finding.xmlLine().orElse(null);
                em.persist(row);
            }
            em.flush();
        } catch (PersistenceException e) {
            throw new InterchangeException("INV-IXC-003", Failure.CONFLICT,
                    "импорт с тем же ключом идемпотентности выполняется параллельно", Map.of());
        }
    }

    private ImportSession toDomain(ImportSessionEntity e) {
        List<ImportFinding> findings = em.createQuery(
                        "select f from ImportFindingEntity f where f.sessionId = :s order by f.ordinal",
                        ImportFindingEntity.class)
                .setParameter("s", e.id).getResultList().stream()
                .map(f -> new ImportFinding(new FindingId(f.id), Severity.valueOf(f.severity), f.code, f.message,
                        Optional.ofNullable(f.archiId).filter(ArchiId::isValid).map(ArchiId::of),
                        Optional.ofNullable(f.xmlLine)))
                .toList();
        ImportRequest request = new ImportRequest(new WorkspaceId(e.workspaceId),
                Optional.ofNullable(e.targetModelId).map(ModelId::new), e.sourceName, new ContentHash(e.sourceHash),
                e.sourceSize, e.idempotencyKey, e.strictMode, e.startedBy);
        return ImportSession.restore(new ImportSessionId(e.id), request, e.startedAt, ImportStatus.valueOf(e.status),
                findings, Optional.ofNullable(e.finishedAt), Optional.ofNullable(e.modelId).map(ModelId::new),
                e.versionNo == null ? 0 : e.versionNo);
    }
}
