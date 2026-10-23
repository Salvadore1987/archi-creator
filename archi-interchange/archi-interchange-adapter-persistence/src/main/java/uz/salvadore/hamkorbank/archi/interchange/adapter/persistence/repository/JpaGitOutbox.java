package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence.repository;

import com.fasterxml.uuid.impl.UUIDUtil;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Очередь публикации в Git поверх {@code outbox_event}: события
 * {@code ModelVersionCommittedEvent}, ключ — модель, порядок — номер версии.
 * Нагрузка — JSON с пространством, моделью и номером версии.
 */
@Repository
public class JpaGitOutbox implements GitOutbox {

    static final String EVENT_TYPE = "ModelVersionCommitted";
    private static final String OPEN = "status in ('PENDING', 'DIVERGED')";
    private static final String COLUMNS = "id, payload ->> 'workspaceId', payload ->> 'modelId', "
            + "(payload ->> 'versionNo')::bigint, status, attempts, next_attempt_at";

    private final EntityManager em;

    private final UuidV7 ids;

    public JpaGitOutbox(EntityManager em, Clock clock) {
        this.em = em;
        this.ids = new UuidV7(clock);
    }

    @Override
    public void enqueue(WorkspaceId workspace, ModelId model, long versionNo, Instant at) {
        String payload = "{\"workspaceId\":\"" + workspace.value() + "\",\"modelId\":\"" + model.value()
                + "\",\"versionNo\":" + versionNo + "}";
        em.createNativeQuery("insert into outbox_event (id, event_type, partition_key, payload, created_at) "
                        + "values (:id, :type, :key, cast(:payload as jsonb), :at)")
                .setParameter("id", ids.next()).setParameter("type", EVENT_TYPE)
                .setParameter("key", model.value()).setParameter("payload", payload)
                .setParameter("at", at).executeUpdate();
    }

    @Override
    public List<Entry> heads() {
        List<?> rows = em.createNativeQuery("select distinct on (partition_key) " + COLUMNS + " from outbox_event "
                        + "where event_type = :type and " + OPEN
                        + " order by partition_key, (payload ->> 'versionNo')::bigint")
                .setParameter("type", EVENT_TYPE).getResultList();
        return rows.stream().map(JpaGitOutbox::entry).toList();
    }

    @Override
    public Optional<Entry> head(ModelId model) {
        List<?> rows = em.createNativeQuery("select " + COLUMNS + " from outbox_event "
                        + "where event_type = :type and partition_key = :key and " + OPEN
                        + " order by (payload ->> 'versionNo')::bigint limit 1")
                .setParameter("type", EVENT_TYPE).setParameter("key", model.value()).getResultList();
        return rows.stream().findFirst().map(JpaGitOutbox::entry);
    }

    @Override
    public int open(ModelId model) {
        return ((Number) em.createNativeQuery("select count(*) from outbox_event "
                        + "where event_type = :type and partition_key = :key and " + OPEN)
                .setParameter("type", EVENT_TYPE).setParameter("key", model.value()).getSingleResult()).intValue();
    }

    @Override
    public void done(UUID id, Instant at) {
        close(id, "DONE", at);
    }

    @Override
    public void skipped(UUID id, Instant at) {
        close(id, "SKIPPED", at);
    }

    @Override
    public void diverged(UUID id, String reason, Instant at) {
        em.createNativeQuery("update outbox_event set status = 'DIVERGED', attempts = attempts + 1, "
                        + "last_error = :reason, next_attempt_at = null where id = :id and " + OPEN)
                .setParameter("reason", reason).setParameter("id", id).executeUpdate();
    }

    @Override
    public void supersede(ModelId model, long throughVersionNo, Instant at) {
        em.createNativeQuery("update outbox_event set status = 'SKIPPED', processed_at = :at, next_attempt_at = null "
                        + "where event_type = :type and partition_key = :key and " + OPEN
                        + " and (payload ->> 'versionNo')::bigint <= :through")
                .setParameter("at", at).setParameter("type", EVENT_TYPE).setParameter("key", model.value())
                .setParameter("through", throughVersionNo).executeUpdate();
    }

    @Override
    public void retryLater(UUID id, String reason, Instant next) {
        em.createNativeQuery("update outbox_event set status = 'PENDING', attempts = attempts + 1, "
                        + "last_error = :reason, next_attempt_at = :next where id = :id and " + OPEN)
                .setParameter("reason", String.valueOf(reason)).setParameter("next", next)
                .setParameter("id", id).executeUpdate();
    }

    private void close(UUID id, String status, Instant at) {
        em.createNativeQuery("update outbox_event set status = :status, processed_at = :at, next_attempt_at = null "
                        + "where id = :id and " + OPEN)
                .setParameter("status", status).setParameter("at", at).setParameter("id", id).executeUpdate();
    }

    @Override
    public int purgeClosed(Instant before) {
        return em.createNativeQuery("delete from outbox_event where event_type = :type "
                        + "and status in ('DONE', 'SKIPPED') and processed_at < :before")
                .setParameter("type", EVENT_TYPE).setParameter("before", before).executeUpdate();
    }

    private static Entry entry(Object row) {
        Object[] r = (Object[]) row;
        return new Entry((UUID) r[0], new WorkspaceId(uuid(r[1])), new ModelId(uuid(r[2])),
                ((Number) r[3]).longValue(), Status.valueOf((String) r[4]), ((Number) r[5]).intValue(),
                r[6] == null ? null : instant(r[6]));
    }

    private static UUID uuid(Object value) {
        return UUIDUtil.uuid((String) value);
    }

    private static Instant instant(Object value) {
        return switch (value) {
            case Instant i -> i;
            case OffsetDateTime o -> o.toInstant();
            case Timestamp t -> t.toInstant();
            default -> OffsetDateTime.parse(value.toString()).withOffsetSameInstant(ZoneOffset.UTC).toInstant();
        };
    }
}
