package uz.salvadore.hamkorbank.archi.modeling.domain.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class IdempotencyRecordTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    @DisplayName("INV-MDL-003: отпечаток тела команды — SHA-256 строчными шестнадцатеричными цифрами")
    void fingerprintMustBeSha256(String fingerprint) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> IdempotencyRecord.record("SaveModel", "architect-1", "key-1", fingerprint, "1", Models.NOW));

        assertEquals(ModelingMessages.FINGERPRINT_NOT_SHA256, rejected.reason().key());
    }
}
