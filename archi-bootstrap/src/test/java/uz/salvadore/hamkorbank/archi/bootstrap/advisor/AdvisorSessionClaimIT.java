package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorSessionRepository;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;

class AdvisorSessionClaimIT extends AdvisorApiTest {

    private static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");

    @Autowired
    private AdvisorSessionRepository sessions;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    @DisplayName("INV-ADV-003: второе занятие того же ключа ждёт коммита первого и отказывает без ошибки уникальности")
    void parallelClaimOfSameKeyWaitsAndLoses() throws Exception {
        WorkspaceId workspace = WorkspaceId.of(jdbc.queryForObject("select id from workspace limit 1", UUID.class));
        TransactionTemplate tx = new TransactionTemplate(transactions);
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            try {
                Future<Boolean> first = pool.submit(() -> tx.execute(status -> {
                    boolean won = sessions.claim(session(workspace, "00000000-0000-7000-8000-0000000000c1"));
                    claimed.countDown();
                    await(release);
                    return won;
                }));
                assertThat(claimed.await(10, TimeUnit.SECONDS)).isTrue();
                Future<Boolean> second = pool.submit(() -> tx.execute(
                        status -> sessions.claim(session(workspace, "00000000-0000-7000-8000-0000000000c2"))));
                Thread.sleep(300);
                assertThat(second.isDone()).as("второе занятие ждёт коммита первого").isFalse();
                release.countDown();

                assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
            } finally {
                release.countDown();
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from advisor_session", Long.class)).isEqualTo(1L);
    }

    private static AdvisorSession session(WorkspaceId workspace, String id) {
        AdvisorRequest review = AdvisorRequest.review(ModelId.of(UUIDUtil.uuid("00000000-0000-7000-8000-0000000000b1")),
                null);
        return AdvisorSession.request(AdvisorSessionId.of(id), workspace, review, "claim-key", "claimer", NOW);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
