package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotUnreadableException;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

class ModelingModelStatesTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Test
    @DisplayName("Содержимое коммита не раскладывается — повреждённый документ 422, а не дефект сервера")
    void unreadableCommitIsCorruptDocument() {
        InvalidValueException defect = new InvalidValueException(InterchangeMessages.ROOT_FOLDER_UNTYPED, "id-f");
        ModelExchangeApplicationService models = (ModelExchangeApplicationService) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {ModelExchangeApplicationService.class},
                (proxy, method, args) -> {
                    throw new SnapshotUnreadableException(defect);
                });

        InterchangeException failure = assertThrows(InterchangeException.class,
                () -> new ModelingModelStates(models).replace(EditorIdentity.of("architect", Role.ARCHITECT),
                        new ModelId(IDS.generate()), new byte[0], "из Git"));

        assertEquals(InterchangeCodes.CORRUPT_DOCUMENT, failure.code());
        assertEquals(Failure.UNPROCESSABLE, failure.failure());
        assertEquals(Message.of(InterchangeMessages.IMPORT_CONTENT_REJECTED, defect.reason()), failure.reason());
    }
}
