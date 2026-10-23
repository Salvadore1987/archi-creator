package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.repository;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;

/**
 * Какой отказ записи — конфликт с параллельным писателем: занятый уникальный ключ или устаревшая
 * версия строки. Всё прочее (нарушение {@code CHECK}, таймаут, обрыв соединения) конфликтом не считается
 * и летит дальше: ответ «повторите позже» спрятал бы дефект.
 */
final class WriteConflicts {

    private static final String UNIQUE_VIOLATION = "23505";

    private WriteConflicts() {
    }

    /** Отказ — проигранная гонка за ключ или версию строки. */
    static boolean isConflict(PersistenceException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof StaleStateException) {
                return true;
            }
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getKind() == ConstraintKind.UNIQUE
                        || UNIQUE_VIOLATION.equals(violation.getSQLState());
            }
        }
        return false;
    }
}
