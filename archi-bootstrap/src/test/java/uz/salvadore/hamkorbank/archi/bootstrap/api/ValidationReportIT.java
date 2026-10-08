package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

class ValidationReportIT extends ApiTest {

    @Test
    @DisplayName("INV-MDL-007, FR-10: нарушение из импорта не блокирует, а попадает в отчёт валидации")
    void importedViolationsAppearInReport() throws Exception {
        UUID model = id(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "violation.archimate", "application/xml",
                                Fixtures.withMatrixViolation()))
                        .with(architect()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.findings[0].code").value("RELATION_NOT_PERMITTED"))
                .andReturn(), "$.modelId");

        mvc.perform(get("/api/v1/models/" + model + "/validate").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("RELATION_NOT_PERMITTED"))
                .andExpect(jsonPath("$[0].severity").value("ERROR"))
                .andExpect(jsonPath("$[0].targetId").value("id-r1"));
    }

    private static UUID id(org.springframework.test.web.servlet.MvcResult result, String path) throws Exception {
        return UUID.fromString(read(result, path));
    }
}
