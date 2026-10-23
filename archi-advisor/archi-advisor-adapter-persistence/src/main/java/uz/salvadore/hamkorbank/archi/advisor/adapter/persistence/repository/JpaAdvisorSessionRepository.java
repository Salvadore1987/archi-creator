package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence.repository;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.advisor.adapter.persistence.entity.AdvisorSessionEntity;
import uz.salvadore.hamkorbank.archi.advisor.adapter.persistence.entity.AuditLogEntity;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSessionRepository;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AuditEntryId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AuditEntry;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.SessionStatus;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

/**
 * Сессии помощника и их записи аудита. Запись аудита вставляется вместе с терминальным
 * состоянием сессии и больше не меняется; повторное сохранение её не дублирует.
 */
@Repository
public class JpaAdvisorSessionRepository implements AdvisorSessionRepository {

    private final EntityManager em;

    public JpaAdvisorSessionRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<AdvisorSession> findByKey(String requestedBy, String idempotencyKey) {
        return em.createQuery("select s from AdvisorSessionEntity s where s.requestedBy = :u and s.idempotencyKey = :k",
                        AdvisorSessionEntity.class)
                .setParameter("u", requestedBy).setParameter("k", idempotencyKey)
                .getResultStream().findFirst().map(this::toDomain);
    }

    /**
     * Блокировка ключа до конца транзакции: параллельный {@code claim} того же ключа ждёт коммита
     * и находит уже записанную сессию. Уникальность ключа в схеме остаётся последней защитой.
     */
    @Override
    public boolean claim(AdvisorSession session) {
        em.createNativeQuery("select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) claimed")
                .setParameter("key", session.requestedBy() + "\n" + session.idempotencyKey())
                .getSingleResult();
        if (findByKey(session.requestedBy(), session.idempotencyKey()).isPresent()) {
            return false;
        }
        save(session);
        return true;
    }

    @Override
    public void save(AdvisorSession session) {
        AdvisorSessionEntity row = em.find(AdvisorSessionEntity.class, session.id().value());
        boolean fresh = row == null;
        if (fresh) {
            row = new AdvisorSessionEntity();
            row.id = session.id().value();
            row.workspaceId = session.workspaceId().value();
            row.modelId = session.modelId().value();
            row.function = session.function().name();
            row.viewId = session.viewId() == null ? null : session.viewId().value();
            row.elementArchiId = session.elementId();
            row.idempotencyKey = session.idempotencyKey();
            row.fingerprint = session.fingerprint();
            row.requestedBy = session.requestedBy();
            row.requestedAt = session.requestedAt();
        }
        row.status = session.status().name();
        row.contextTokens = session.contextSize();
        row.contextTruncated = session.contextTruncated();
        row.promptTokens = session.usage().promptTokens();
        row.completionTokens = session.usage().completionTokens();
        row.rejectionReason = session.rejectionReason() == null ? null : session.rejectionReason().name();
        row.result = ResultCodec.encode(session.findings(), session.plan(), session.answer());
        row.firstTokenAt = session.firstTokenAt();
        row.finishedAt = session.finishedAt();
        if (fresh) {
            em.persist(row);
        }
        AuditEntry entry = session.auditEntry();
        if (entry != null && em.find(AuditLogEntity.class, entry.id().value()) == null) {
            em.persist(audit(session, entry));
        }
        em.flush();
    }

    private static AuditLogEntity audit(AdvisorSession session, AuditEntry entry) {
        AuditLogEntity row = new AuditLogEntity();
        row.id = entry.id().value();
        row.workspaceId = session.workspaceId().value();
        row.modelId = session.modelId().value();
        row.sessionId = session.id().value();
        row.userSubject = entry.actor();
        row.action = entry.function().name();
        row.outcome = entry.outcome().name();
        row.rejectionReason = entry.rejectionReason() == null ? null : entry.rejectionReason().name();
        row.promptTokens = Math.toIntExact(entry.usage().promptTokens());
        row.completionTokens = Math.toIntExact(entry.usage().completionTokens());
        row.createdAt = entry.createdAt();
        return row;
    }

    private AdvisorSession toDomain(AdvisorSessionEntity row) {
        ResultCodec.Decoded result = ResultCodec.decode(row.result);
        TokenUsage usage = new TokenUsage(row.promptTokens, row.completionTokens);
        AuditEntry audit = em.createQuery("select a from AuditLogEntity a where a.sessionId = :s",
                        AuditLogEntity.class).setParameter("s", row.id).getResultStream().findFirst()
                .map(a -> new AuditEntry(AuditEntryId.of(a.id), a.userSubject, AdvisorFunction.valueOf(a.action),
                        new TokenUsage(a.promptTokens, a.completionTokens), SessionStatus.valueOf(a.outcome),
                        reason(a.rejectionReason), a.createdAt))
                .orElse(null);
        return AdvisorSession.restore(AdvisorSessionId.of(row.id), WorkspaceId.of(row.workspaceId),
                ModelId.of(row.modelId), AdvisorFunction.valueOf(row.function),
                row.viewId == null ? null : ViewId.of(row.viewId), row.elementArchiId,
                row.idempotencyKey, row.fingerprint, row.requestedBy, row.requestedAt,
                SessionStatus.valueOf(row.status), row.contextTokens, row.contextTruncated,
                usage, result.findings(), result.plan(), result.answer(),
                reason(row.rejectionReason), row.firstTokenAt, row.finishedAt, audit);
    }

    private static @Nullable RejectionReason reason(@Nullable String stored) {
        return stored == null ? null : RejectionReason.valueOf(stored);
    }
}
