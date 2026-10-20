package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportAllViewImagesQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportViewImageQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.RasterGate;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/** Порядок проверок картинок: роль, затем параметры, затем чтение представления и снимка. */
class ViewImageOrderTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();
    private static final EditorIdentity VIEWER = EditorIdentity.of("viewer", Role.VIEWER);
    private static final EditorIdentity NOBODY = EditorIdentity.of("nobody");
    private static final RasterGate GATE = new RasterGate(64_000_000, 1, Duration.ofSeconds(1));

    private final InterchangeKernel kernel = new InterchangeKernel(new ImportFakes.Transactions(), events -> { },
            InterchangeMetrics.NONE, message -> message.key(), ImportFakes.CLOCK);
    private final ViewImageExportQueryHandler one =
            new ViewImageExportQueryHandler(kernel, null, null, null, null, null, GATE);
    private final AllViewImagesExportQueryHandler all =
            new AllViewImagesExportQueryHandler(kernel, null, null, null, null, GATE);

    @Test
    @DisplayName("FR-28: без роли и с неизвестным форматом — 403, а не отказ в значении параметра")
    void roleIsCheckedBeforeOptions() {
        InterchangeException single = assertThrows(InterchangeException.class, () -> one.handle(NOBODY,
                new ExportViewImageQuery(IDS.generate(), "gif", null, null, null, null)));
        InterchangeException batch = assertThrows(InterchangeException.class, () -> all.handle(NOBODY,
                new ExportAllViewImagesQuery(IDS.generate(), "gif", null, null, null, null)));

        assertEquals(InterchangeCodes.ACCESS_DENIED, single.code());
        assertEquals("ExportViewImage", single.details().get("operation"));
        assertEquals(InterchangeCodes.ACCESS_DENIED, batch.code());
        assertEquals("ExportAllViewImages", batch.details().get("operation"));
    }

    @Test
    @DisplayName("UC-IXC-004: формат и плотность вне списка — отказ до чтения представления")
    void optionsAreCheckedBeforeReading() {
        assertEquals(InterchangeCodes.UNKNOWN_FORMAT, assertThrows(InterchangeException.class, () -> one.handle(
                VIEWER, new ExportViewImageQuery(IDS.generate(), "gif", null, null, null, null))).code());
        assertEquals(InterchangeCodes.INVALID_EXPORT_OPTION, assertThrows(InterchangeException.class,
                () -> all.handle(VIEWER, new ExportAllViewImagesQuery(IDS.generate(), "png", 3, null, null, null)))
                .code());
    }
}
