package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.QueryTimeoutException;
import java.sql.SQLException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WriteConflictsTest {

    @Test
    @DisplayName("занятый уникальный ключ и устаревшая версия — конфликт")
    void uniqueKeyAndStaleVersionAreConflicts() {
        assertTrue(WriteConflicts.isConflict(violation(ConstraintKind.UNIQUE, "23505")));
        assertTrue(WriteConflicts.isConflict(new PersistenceException(violation(ConstraintKind.OTHER, "23505"))));
        assertTrue(WriteConflicts.isConflict(new OptimisticLockException()));
        assertTrue(WriteConflicts.isConflict(new PersistenceException(new StaleObjectStateException("row", 1))));
    }

    @Test
    @DisplayName("нарушение CHECK, таймаут и прочие отказы хранения — не конфликт")
    void otherFailuresAreNotConflicts() {
        assertFalse(WriteConflicts.isConflict(violation(ConstraintKind.CHECK, "23514")));
        assertFalse(WriteConflicts.isConflict(violation(ConstraintKind.NOT_NULL, "23502")));
        assertFalse(WriteConflicts.isConflict(new QueryTimeoutException()));
        assertFalse(WriteConflicts.isConflict(new PersistenceException(new SQLException("gone", "08006"))));
    }

    private static ConstraintViolationException violation(ConstraintKind kind, String sqlState) {
        return new ConstraintViolationException("violation", new SQLException("violation", sqlState), kind, "ck");
    }
}
