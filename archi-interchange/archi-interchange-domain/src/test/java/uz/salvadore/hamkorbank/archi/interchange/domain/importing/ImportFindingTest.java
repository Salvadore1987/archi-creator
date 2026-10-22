package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uz.salvadore.hamkorbank.archi.interchange.domain.importing.Sessions.UUIDS;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;

class ImportFindingTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    @DisplayName("находка импорта без кода правила не строится: клиент ветвится по коду")
    void findingWithoutCodeIsRejected(String code) {
        FindingId id = FindingId.next(UUIDS);
        Message message = ImportFinding.storedText("связь без цели");

        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> new ImportFinding(id, Severity.ERROR, code, message, null, null));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.FINDING_WITHOUT_CODE, e.reason().key());
    }
}
