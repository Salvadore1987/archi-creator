package uz.salvadore.hamkorbank.archi.modeling.domain.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class WorkspaceTest {

    @ParameterizedTest
    @MethodSource("invalidNames")
    @DisplayName("Имя рабочего пространства — непустое и не длиннее предела")
    void invalidNameIsRejected(String name) {
        InvalidValueException rejected = assertThrows(InvalidValueException.class,
                () -> Workspace.create(WorkspaceId.next(Models.UUIDS), name, Models.NOW));

        assertEquals(ModelingMessages.WORKSPACE_NAME_INVALID, rejected.reason().key());
    }

    static Stream<String> invalidNames() {
        return Stream.of(null, "", "   ", "w".repeat(Workspace.NAME_MAX + 1));
    }
}
