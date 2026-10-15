package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotencyRecord;

/** Ключи идемпотентности команд. Гонка двух одинаковых ключей — конфликт ключа. */
public interface IdempotencyRepository {

    /** Запись ключа идемпотентности команды данного автора в данной области. */
    Optional<IdempotencyRecord> find(String scope, String actor, String key);

    /** Сохраняет запись; занятый ключ — конфликт. */
    void save(IdempotencyRecord record);
}
