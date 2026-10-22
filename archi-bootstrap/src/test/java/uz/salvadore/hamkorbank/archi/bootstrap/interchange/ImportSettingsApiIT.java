package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;

/** Строгость импорта пространства через REST: читают те, кто импортирует, меняет администратор. */
class ImportSettingsApiIT extends ApiTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    private UUID workspace;

    @BeforeEach
    void lenientWorkspace() {
        workspace = workspace().value();
        jdbc.update("update workspace set strict_import = false");
    }

    @AfterEach
    void restoreLenient() {
        jdbc.update("update workspace set strict_import = false");
    }

    @Test
    @DisplayName("FR-49: архитектор читает строгость импорта — 200 со значением из базы")
    void architectReadsSettings() throws Exception {
        jdbc.update("update workspace set strict_import = true where id = ?", workspace);

        mvc.perform(get(settings(workspace)).with(architect()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strictImport").value(true));
    }

    @Test
    @DisplayName("FR-49: наблюдатель не читает строгость импорта — 403 IXC_ACCESS_DENIED")
    void viewerCannotRead() throws Exception {
        mvc.perform(get(settings(workspace)).with(viewer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(InterchangeCodes.ACCESS_DENIED));
    }

    @Test
    @DisplayName("Строгость импорта неизвестного пространства — 404 IXC_NOT_FOUND")
    void unknownWorkspaceIsNotFoundOnRead() throws Exception {
        mvc.perform(get(settings(IDS.generate())).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(InterchangeCodes.NOT_FOUND));
    }

    @Test
    @DisplayName("FR-49: администратор включает строгий режим — 200, чтение отдаёт новое значение")
    void adminChangesSettings() throws Exception {
        mvc.perform(put(settings(workspace)).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strictImport\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strictImport").value(true));

        mvc.perform(get(settings(workspace)).with(architect()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strictImport").value(true));
    }

    @Test
    @DisplayName("FR-49: архитектор не меняет строгость импорта — 403 IXC_ACCESS_DENIED, значение прежнее")
    void architectCannotChange() throws Exception {
        mvc.perform(put(settings(workspace)).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strictImport\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(InterchangeCodes.ACCESS_DENIED));

        mvc.perform(get(settings(workspace)).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strictImport").value(false));
    }

    @Test
    @DisplayName("Смена строгости неизвестного пространства — 404 IXC_NOT_FOUND")
    void unknownWorkspaceIsNotFoundOnChange() throws Exception {
        mvc.perform(put(settings(IDS.generate())).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strictImport\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(InterchangeCodes.NOT_FOUND));
    }

    private static String settings(UUID workspace) {
        return "/api/v1/workspaces/" + workspace + "/import-settings";
    }
}
