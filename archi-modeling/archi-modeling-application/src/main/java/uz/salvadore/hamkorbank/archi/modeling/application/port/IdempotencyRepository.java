package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotencyRecord;

/** Ключи идемпотентности команд (INV-MDL-003). Гонка двух одинаковых ключей — конфликт ключа. */
public interface IdempotencyRepository {

    Optional<IdempotencyRecord> find(String scope, String actor, String key);

    void save(IdempotencyRecord record);
}
