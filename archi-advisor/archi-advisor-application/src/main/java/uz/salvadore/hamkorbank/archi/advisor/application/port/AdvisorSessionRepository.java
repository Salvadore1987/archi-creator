package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;

/** Сессии помощника. Ключ идемпотентности уникален в пределах автора. */
public interface AdvisorSessionRepository {

    /** Сессия автора с этим ключом идемпотентности; нет — пусто. */
    Optional<AdvisorSession> findByKey(String requestedBy, String idempotencyKey);

    /** Новая сессия или новое состояние существующей — вместе с её записью аудита, если она есть. */
    void save(AdvisorSession session);
}
