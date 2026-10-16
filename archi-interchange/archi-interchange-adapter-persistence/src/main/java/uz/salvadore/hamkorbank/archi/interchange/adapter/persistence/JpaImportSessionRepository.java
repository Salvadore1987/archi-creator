package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
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
 * Сессии импорта и их ключи идемпотентности. Документ не хранится — только статус,
 * отчёт и результат: повтор по ключу отдаёт их, а не применяет файл снова.
 */
@Repository
public class JpaImportSessionRepository implements ImportSessionRepository {

    private final EntityManager em;

    private final TextCatalog texts;

    /** Находка хранится текстом на языке запроса, в котором прошёл импорт: это отчёт, а не сообщение. */
    public JpaImportSessionRepository(EntityManager em, TextCatalog texts) {
        this.em = em;
        this.texts = texts;
    }

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
        entity.targetModelId = session.targetModelId() == null ? null : session.targetModelId().value();
        entity.sourceName = session.sourceName();
        entity.sourceHash = session.sourceHash().value();
        entity.sourceSize = session.sourceSize();
        entity.idempotencyKey = session.idempotencyKey();
        entity.strictMode = session.strictMode();
        entity.status = session.status().name();
        entity.startedBy = session.startedBy();
        entity.startedAt = session.startedAt();
        entity.finishedAt = session.finishedAt();
        entity.modelId = session.appliedModelId() == null ? null : session.appliedModelId().value();
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
                row.message = texts.text(finding.message());
                row.archiId = finding.archiId() == null ? null : finding.archiId().value();
                row.xmlLine = finding.xmlLine();
                em.persist(row);
            }
            em.flush();
        } catch (PersistenceException e) {
            throw new InterchangeException(InterchangeCodes.IMPORT_IDEMPOTENCY, Failure.CONFLICT,
                    Message.of(InterchangeMessages.IMPORT_IN_PROGRESS), Map.of());
        }
    }

    private ImportSession toDomain(ImportSessionEntity e) {
        List<ImportFinding> findings = em.createQuery(
                        "select f from ImportFindingEntity f where f.sessionId = :s order by f.ordinal",
                        ImportFindingEntity.class)
                .setParameter("s", e.id).getResultList().stream()
                .map(f -> new ImportFinding(new FindingId(f.id), Severity.valueOf(f.severity), f.code,
                        ImportFinding.storedText(f.message),
                        f.archiId != null && ArchiId.isValid(f.archiId) ? ArchiId.of(f.archiId) : null,
                        f.xmlLine))
                .toList();
        ImportRequest request = new ImportRequest(new WorkspaceId(e.workspaceId),
                e.targetModelId == null ? null : new ModelId(e.targetModelId), e.sourceName,
                new ContentHash(e.sourceHash), e.sourceSize, e.idempotencyKey, e.strictMode, e.startedBy);
        return ImportSession.restore(new ImportSessionId(e.id), request, e.startedAt, ImportStatus.valueOf(e.status),
                findings, e.finishedAt, e.modelId == null ? null : new ModelId(e.modelId),
                e.versionNo == null ? 0 : e.versionNo);
    }
}
