package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;

/** Сессии помощника. Ключ идемпотентности уникален в пределах автора. */
public interface AdvisorSessionRepository {

    /** Сессия автора с этим ключом идемпотентности; нет — пусто. */
    Optional<AdvisorSession> findByKey(String requestedBy, String idempotencyKey);

    /**
     * Занять ключ автора новой сессией — атомарно относительно параллельного обращения с тем же ключом.
     * Ключ уже занят — ничего не пишется.
     *
     * @return {@code true} — сессия записана; {@code false} — ключ занят другой сессией
     */
    boolean claim(AdvisorSession session);

    /** Новая сессия или новое состояние существующей — вместе с её записью аудита, если она есть. */
    void save(AdvisorSession session);
}
