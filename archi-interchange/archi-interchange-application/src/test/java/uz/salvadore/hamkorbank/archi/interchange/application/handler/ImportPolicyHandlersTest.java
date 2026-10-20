package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureImportPolicyCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetImportPolicyQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportPolicyResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

class ImportPolicyHandlersTest {

    private static final EditorIdentity VIEWER = EditorIdentity.of("viewer", Role.VIEWER);
    private static final EditorIdentity ADMIN = EditorIdentity.of("admin", Role.ADMIN);

    private final Map<WorkspaceId, Boolean> rows = new HashMap<>();
    private final ImportPolicy policy = new ImportPolicy() {
        @Override
        public boolean strictImport(WorkspaceId workspaceId) {
            return rows.getOrDefault(workspaceId, false);
        }

        @Override
        public void setStrictImport(WorkspaceId workspaceId, boolean strict) {
            rows.put(workspaceId, strict);
        }
    };
    private final InterchangeKernel kernel = new InterchangeKernel(new ImportFakes.Transactions(), events -> { },
            InterchangeMetrics.NONE, message -> message.key(), ImportFakes.CLOCK);
    private final ImportPolicyGetQueryHandler get = new ImportPolicyGetQueryHandler(kernel, policy);
    private final ImportPolicyConfigureCommandHandler configure =
            new ImportPolicyConfigureCommandHandler(kernel, policy);

    @Test
    @DisplayName("FR-49: смена строгости отдаёт итоговую настройку — второго вызова не нужно")
    void configureReturnsResultingPolicy() {
        ImportPolicyResponse result = configure.handle(ADMIN,
                new ConfigureImportPolicyCommand(ImportFakes.WORKSPACE, true));

        assertEquals(ImportFakes.WORKSPACE, result.workspaceId());
        assertTrue(result.strictImport());
        assertTrue(get.handle(ImportFakes.ARCHITECT, new GetImportPolicyQuery(ImportFakes.WORKSPACE)).strictImport());
    }

    @Test
    @DisplayName("FR-28: чтение настройки — операция с ролью, VIEWER её не читает")
    void readingNeedsRole() {
        InterchangeException denied = assertThrows(InterchangeException.class,
                () -> get.handle(VIEWER, new GetImportPolicyQuery(ImportFakes.WORKSPACE)));

        assertEquals(InterchangeCodes.ACCESS_DENIED, denied.code());
    }

    @Test
    @DisplayName("FR-49: строгость меняет только ADMIN — ARCHITECT получает отказ, настройка не меняется")
    void onlyAdminConfigures() {
        assertThrows(InterchangeException.class, () -> configure.handle(ImportFakes.ARCHITECT,
                new ConfigureImportPolicyCommand(ImportFakes.WORKSPACE, true)));

        assertFalse(policy.strictImport(new WorkspaceId(ImportFakes.WORKSPACE)));
    }
}
