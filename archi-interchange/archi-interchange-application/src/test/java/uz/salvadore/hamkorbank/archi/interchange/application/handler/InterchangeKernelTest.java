package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

class InterchangeKernelTest {

    private static final EditorIdentity VIEWER = new EditorIdentity("viewer", Set.of(Role.VIEWER), Set.of());
    private static final EditorIdentity ARCHITECT = new EditorIdentity("architect", Set.of(Role.ARCHITECT), Set.of());

    private final List<String> observed = new ArrayList<>();
    private final InterchangeKernel kernel = new InterchangeKernel(new DirectUnitOfWork(), events -> { },
            new RecordingMetrics(), message -> message.key(),
            Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC));

    @Test
    @DisplayName("FR-28: без роли — отказ до работы сценария, и он попадает в метрику сценария")
    void roleIsCheckedBeforeWork() {
        boolean[] worked = {false};

        InterchangeException denied = assertThrows(InterchangeException.class,
                () -> kernel.run(InterchangeOperation.IMPORT_ARCHIMATE_FILE, VIEWER, () -> {
                    worked[0] = true;
                    return null;
                }));

        assertEquals(InterchangeCodes.ACCESS_DENIED, denied.code());
        assertFalse(worked[0], "работа без роли не начиналась");
        assertEquals(List.of("ImportArchimateFile"), observed);
    }

    @Test
    @DisplayName("FR-28: с ролью — результат работы под метрикой операции")
    void allowedWorkReturnsItsResult() {
        assertEquals("done", kernel.run(InterchangeOperation.IMPORT_ARCHIMATE_FILE, ARCHITECT, () -> "done"));
        assertEquals(List.of("ImportArchimateFile"), observed);
    }

    /** Метрика, которая записывает имя сценария и пропускает отказ дальше. */
    private final class RecordingMetrics implements InterchangeMetrics {

        @Override
        public <T> T observe(String useCase, Supplier<T> work) {
            observed.add(useCase);
            return work.get();
        }
    }

    private static final class DirectUnitOfWork implements UnitOfWork {

        @Override
        public <T> T write(Supplier<T> work) {
            return work.get();
        }

        @Override
        public <T> T read(Supplier<T> work) {
            return work.get();
        }
    }
}
