package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.time.Clock;
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

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    private final ExportService service = new ExportService(null, null, InterchangeMetrics.NONE,
            message -> message.key(), null, null, Clock.systemUTC());
    private final EditorIdentity viewer = EditorIdentity.of("viewer", Role.VIEWER);

    @Test
    @DisplayName("Неизвестное имя формата — отказ IXC_UNKNOWN_FORMAT")
    void unknownFormat() {
        InterchangeException e = assertThrows(InterchangeException.class,
                () -> service.export(viewer, IDS.generate(), request("pdf")));
        assertEquals(InterchangeCodes.UNKNOWN_FORMAT, e.code());
        assertEquals(Failure.UNPROCESSABLE, e.failure());
    }

    @Test
    @DisplayName("UC-IXC-005: oef обслуживается сценарием ExportOpenExchange — роль проверяется до чтения модели")
    void openExchangeIsServedByItsUseCase() {
        InterchangeException e = assertThrows(InterchangeException.class,
                () -> service.export(EditorIdentity.of("nobody"), IDS.generate(), request(ExportService.FORMAT_OEF)));
        assertEquals(InterchangeCodes.ACCESS_DENIED, e.code());
        assertEquals("ExportOpenExchange", e.details().get("operation"));
    }

    private static ExportRequest request(String format) {
        return new ExportRequest(format, null, null, null);
    }
}
