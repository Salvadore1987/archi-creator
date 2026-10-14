package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

class ImportGateTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    private final ImportGate gate = new ImportGate(1);
    private final WorkspaceId bank = new WorkspaceId(IDS.generate());
    private final WorkspaceId other = new WorkspaceId(IDS.generate());

    @Test
    @DisplayName("Второй импорт в то же пространство, пока идёт первый, — отказ 409, а не ожидание")
    void secondImportIntoSameWorkspaceIsRefused() {
        InterchangeException busy = assertThrows(InterchangeException.class,
                () -> gate.through(bank, () -> gate.through(bank, () -> "второй")));

        assertEquals(InterchangeCodes.IMPORT_BUSY, busy.code());
        assertEquals(Failure.CONFLICT, busy.failure());
    }

    @Test
    @DisplayName("Импорты в разные пространства идут одновременно")
    void otherWorkspaceIsNotBlocked() {
        assertEquals("другое", gate.through(bank, () -> gate.through(other, () -> "другое")));
    }

    @Test
    @DisplayName("Место освобождается и после отказа импорта")
    void slotIsReleasedAfterFailure() {
        assertThrows(IllegalStateException.class, () -> gate.through(bank, () -> {
            throw new IllegalStateException();
        }));

        assertEquals("снова", gate.through(bank, () -> "снова"));
    }
}
