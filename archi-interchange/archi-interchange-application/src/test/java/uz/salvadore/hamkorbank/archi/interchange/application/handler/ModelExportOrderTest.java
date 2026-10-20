package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportModelQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Порядок проверок выгрузки: роль, затем разбор формата и параметров, затем чтение модели.
 * Чтение модели здесь не нужно — до него отказ не доходит, а дошедший запрос упал бы на фейке.
 */
class ModelExportOrderTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();
    private static final EditorIdentity VIEWER = EditorIdentity.of("viewer", Role.VIEWER);
    private static final EditorIdentity NOBODY = EditorIdentity.of("nobody");

    private final ModelExportQueryHandler handler = new ModelExportQueryHandler(
            new InterchangeKernel(new ImportFakes.Transactions(), events -> { }, InterchangeMetrics.NONE,
                    message -> message.key(), ImportFakes.CLOCK),
            new ImportFakes.Models(new ImportFakes.Transactions()), null, null);

    @Test
    @DisplayName("Неизвестное имя формата — отказ IXC_UNKNOWN_FORMAT до чтения модели")
    void unknownFormat() {
        InterchangeException e = assertThrows(InterchangeException.class,
                () -> handler.handle(VIEWER, query("pdf", null)));

        assertEquals(InterchangeCodes.UNKNOWN_FORMAT, e.code());
        assertEquals(Failure.UNPROCESSABLE, e.failure());
    }

    @Test
    @DisplayName("FR-28: без роли и с неизвестным форматом — 403, а не отказ в значении параметра")
    void roleIsCheckedBeforeFormat() {
        InterchangeException e = assertThrows(InterchangeException.class,
                () -> handler.handle(NOBODY, query("pdf", null)));

        assertEquals(InterchangeCodes.ACCESS_DENIED, e.code());
        assertEquals("ExportArchimate", e.details().get("operation"));
    }

    @Test
    @DisplayName("FR-28: без роли и с недопустимым разделителем CSV — 403, разделитель разбирается после роли")
    void roleIsCheckedBeforeCsvOptions() {
        InterchangeException denied = assertThrows(InterchangeException.class,
                () -> handler.handle(NOBODY, query("csv", "|")));
        InterchangeException invalid = assertThrows(InterchangeException.class,
                () -> handler.handle(VIEWER, query("csv", "|")));

        assertEquals(InterchangeCodes.ACCESS_DENIED, denied.code());
        assertEquals("ExportCatalogCsv", denied.details().get("operation"));
        assertEquals(InterchangeCodes.INVALID_REQUEST, invalid.code());
    }

    @Test
    @DisplayName("UC-IXC-005: oef обслуживается сценарием ExportOpenExchange — роль проверяется до чтения модели")
    void openExchangeIsServedByItsUseCase() {
        InterchangeException e = assertThrows(InterchangeException.class,
                () -> handler.handle(NOBODY, query("oef", null)));

        assertEquals(InterchangeCodes.ACCESS_DENIED, e.code());
        assertEquals("ExportOpenExchange", e.details().get("operation"));
    }

    private static ExportModelQuery query(String format, String separator) {
        return new ExportModelQuery(IDS.generate(), format, null, separator, null);
    }
}
