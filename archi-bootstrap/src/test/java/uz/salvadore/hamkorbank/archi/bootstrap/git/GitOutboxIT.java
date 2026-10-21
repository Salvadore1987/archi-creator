package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;

/** Очередь публикации в базе: переходы только из открытых состояний, закрытые записи чистятся по сроку. */
class GitOutboxIT extends IntegrationTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();
    private static final Instant AT = Instant.parse("2026-10-21T10:00:00Z");

    @Autowired
    private GitOutbox outbox;
    @Autowired
    private UnitOfWork unitOfWork;

    private final ModelId model = new ModelId(IDS.generate());

    @AfterEach
    void clean() {
        jdbc.update("delete from outbox_event where partition_key = ?", model.value());
    }

    @Test
    @DisplayName("INV-IXC-009: закрытую запись второй исполнитель не переоткрывает и не перезакрывает")
    void closedEntryStaysClosed() {
        UUID id = enqueue(1);
        write(() -> outbox.done(id, AT));

        write(() -> outbox.retryLater(id, "Rejected", AT.plusSeconds(5)));
        write(() -> outbox.diverged(id, "head", AT));
        write(() -> outbox.skipped(id, AT));

        assertThat(jdbc.queryForObject("select status from outbox_event where id = ?", String.class, id))
                .isEqualTo("DONE");
        assertThat(jdbc.queryForObject("select attempts from outbox_event where id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    @DisplayName("INV-IXC-009: закрытые записи старше срока удаляются, открытые и свежие остаются")
    void closedEntriesArePurgedByAge() {
        UUID old = enqueue(1);
        UUID fresh = enqueue(2);
        UUID open = enqueue(3);
        write(() -> outbox.done(old, AT.minus(Duration.ofDays(40))));
        write(() -> outbox.skipped(fresh, AT));

        int purged = unitOfWork.write(() -> outbox.purgeClosed(AT.minus(Duration.ofDays(30))));

        assertThat(purged).isEqualTo(1);
        assertThat(jdbc.queryForList("select id from outbox_event where partition_key = ? order by created_at",
                UUID.class, model.value())).containsExactlyInAnyOrder(fresh, open);
    }

    private UUID enqueue(long versionNo) {
        write(() -> outbox.enqueue(new WorkspaceId(workspace().value()), model, versionNo, AT));
        return jdbc.queryForObject("select id from outbox_event where partition_key = ? "
                + "and (payload ->> 'versionNo')::bigint = ?", UUID.class, model.value(), versionNo);
    }

    private void write(Runnable work) {
        unitOfWork.write(() -> {
            work.run();
            return null;
        });
    }
}
