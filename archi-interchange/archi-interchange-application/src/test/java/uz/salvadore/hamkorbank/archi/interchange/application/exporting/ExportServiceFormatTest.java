package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Выбор формата выгрузки — решение сценария, а не адаптера: неизвестный и ещё
 * не реализованный форматы отклоняются сервисом до чтения модели. Версии и события
 * здесь не нужны — до них отказ не доходит.
 */
class ExportServiceFormatTest {

    private final ExportService service = new ExportService(null, null, InterchangeMetrics.NONE,
            message -> message.key(), Clock.systemUTC());
    private final EditorIdentity viewer = EditorIdentity.of("viewer", Role.VIEWER);

    @Test
    @DisplayName("Неизвестное имя формата — отказ IXC_UNKNOWN_FORMAT")
    void unknownFormat() {
        InterchangeException e = assertThrows(InterchangeException.class,
                () -> service.export(viewer, UUID.randomUUID(), request("pdf")));
        assertEquals(InterchangeCodes.UNKNOWN_FORMAT, e.code());
        assertEquals(Failure.UNPROCESSABLE, e.failure());
    }

    @Test
    @DisplayName("Известный, но не реализованный формат — отказ IXC_FORMAT_NOT_AVAILABLE")
    void formatNotAvailable() {
        InterchangeException e = assertThrows(InterchangeException.class,
                () -> service.export(viewer, UUID.randomUUID(), request(ExportService.FORMAT_OEF)));
        assertEquals(InterchangeCodes.FORMAT_NOT_AVAILABLE, e.code());
    }

    private static ExportRequest request(String format) {
        return new ExportRequest(format, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
