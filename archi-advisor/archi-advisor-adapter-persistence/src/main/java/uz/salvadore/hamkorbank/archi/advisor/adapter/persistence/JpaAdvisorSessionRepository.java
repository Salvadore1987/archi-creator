package uz.salvadore.hamkorbank.archi.advisor.adapter.persistence;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.stereotype.Repository;
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
            row.viewId = session.viewId().map(ViewId::value).orElse(null);
            row.elementArchiId = session.elementId().orElse(null);
            row.idempotencyKey = session.idempotencyKey();
            row.fingerprint = session.fingerprint();
            row.requestedBy = session.requestedBy();
            row.requestedAt = session.requestedAt();
        }
        row.status = session.status().name();
        row.contextTokens = session.contextSize().orElse(null);
        row.contextTruncated = session.contextTruncated();
        row.promptTokens = session.usage().promptTokens();
        row.completionTokens = session.usage().completionTokens();
        row.rejectionReason = session.rejectionReason().map(Enum::name).orElse(null);
        row.result = ResultCodec.encode(session.findings(), session.plan(), session.answer());
        row.firstTokenAt = session.firstTokenAt().orElse(null);
        row.finishedAt = session.finishedAt().orElse(null);
        if (fresh) {
            em.persist(row);
        }
        session.auditEntry().ifPresent(entry -> {
            if (em.find(AuditLogEntity.class, entry.id().value()) == null) {
                em.persist(audit(session, entry));
            }
        });
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
        row.rejectionReason = entry.rejectionReason().map(Enum::name).orElse(null);
        row.promptTokens = Math.toIntExact(entry.usage().promptTokens());
        row.completionTokens = Math.toIntExact(entry.usage().completionTokens());
        row.createdAt = entry.createdAt();
        return row;
    }

    private AdvisorSession toDomain(AdvisorSessionEntity row) {
        ResultCodec.Decoded result = ResultCodec.decode(row.result);
        TokenUsage usage = new TokenUsage(row.promptTokens, row.completionTokens);
        Optional<AuditEntry> audit = em.createQuery("select a from AuditLogEntity a where a.sessionId = :s",
                        AuditLogEntity.class).setParameter("s", row.id).getResultStream().findFirst()
                .map(a -> new AuditEntry(AuditEntryId.of(a.id), a.userSubject, AdvisorFunction.valueOf(a.action),
                        new TokenUsage(a.promptTokens, a.completionTokens), SessionStatus.valueOf(a.outcome),
                        Optional.ofNullable(a.rejectionReason).map(RejectionReason::valueOf), a.createdAt));
        return AdvisorSession.restore(AdvisorSessionId.of(row.id), WorkspaceId.of(row.workspaceId),
                ModelId.of(row.modelId), AdvisorFunction.valueOf(row.function),
                Optional.ofNullable(row.viewId).map(ViewId::of), Optional.ofNullable(row.elementArchiId),
                row.idempotencyKey, row.fingerprint, row.requestedBy, row.requestedAt,
                SessionStatus.valueOf(row.status), Optional.ofNullable(row.contextTokens), row.contextTruncated,
                usage, result.findings(), result.plan(), result.answer(),
                Optional.ofNullable(row.rejectionReason).map(RejectionReason::valueOf),
                Optional.ofNullable(row.firstTokenAt), Optional.ofNullable(row.finishedAt), audit);
    }
}
