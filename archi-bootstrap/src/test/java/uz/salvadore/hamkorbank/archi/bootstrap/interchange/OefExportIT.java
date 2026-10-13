package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefSchema;

class OefExportIT extends ApiTest {

    private static final OefSchema SCHEMA = OefSchema.load();

    @Test
    @DisplayName("INV-IXC-006, FR-43: VIEWER получает OEF архивом — файл валиден по XSD, остаток XML в отчёте")
    void lossReportListsRawFragments() throws Exception {
        String model = imported("unknown_extension");

        MockHttpServletResponse response = mvc.perform(get("/api/v1/models/" + model + "/export?fmt=oef")
                        .with(viewer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("X-Archi-Version-No", "1"))
                .andReturn().getResponse();
        Map<String, byte[]> files = unzip(response.getContentAsByteArray());

        assertThat(files.keySet()).containsExactly("neizvestnye-uzly-i-atributy-v1.xml", "loss-report.json");
        assertThat(response.getHeader("Content-Disposition")).contains("-v1.oef.zip");
        byte[] xml = files.get("neizvestnye-uzly-i-atributy-v1.xml");
        assertThat(SCHEMA.violation(xml)).isEmpty();
        assertThat(new String(xml, StandardCharsets.UTF_8))
                .contains("xsi:type=\"ApplicationComponent\"", "<name xml:lang=\"ru\">АБС</name>")
                .doesNotContain("QuantumComponent", "vendor:lineage");

        String report = new String(files.get("loss-report.json"), StandardCharsets.UTF_8);
        List<Map<String, String>> entries = JsonPath.read(report, "$.entries");
        assertThat(response.getHeader("X-Archi-Loss-Count")).isEqualTo(String.valueOf(entries.size()));
        assertThat(JsonPath.<String>read(report, "$.format")).isEqualTo("OPEN_EXCHANGE");
        assertThat(entries).anySatisfy(e -> {
            assertThat(e).containsEntry("archiId", "id-53f986115270259ff902bf66cf3dca2c")
                    .containsEntry("objectKind", "element").containsEntry("lostAspect", "xml:vendor:lineage");
            assertThat(e.get("reason")).contains("vendor:lineage", "не выгружен");
        });
        assertThat(entries).anySatisfy(e -> assertThat(e).containsEntry("objectKind", "model")
                .containsEntry("lostAspect", "xml:vendor:metadata"));
    }

    @Test
    @DisplayName("INV-IXC-004, INV-IXC-008: повтор по той же версии — те же байты; причины — на языке запроса")
    void repeatedExportIsIdenticalAndLocalized() throws Exception {
        String model = imported("styled_objects");
        byte[] first = mvc.perform(get("/api/v1/models/" + model + "/export?fmt=oef").with(viewer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        byte[] second = mvc.perform(get("/api/v1/models/" + model + "/export?fmt=oef&version=1").with(viewer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(second).isEqualTo(first);

        byte[] english = mvc.perform(get("/api/v1/models/" + model + "/export?fmt=oef").with(viewer())
                        .header("Accept-Language", "en"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String report = new String(unzip(english).get("loss-report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("cannot be expressed in Open Exchange Format");
    }

    @Test
    @DisplayName("FR-43: эталонная модель выгружается через API и проходит XSD")
    void referenceModelExportsThroughApi() throws Exception {
        String model = read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "reference.archimate", "application/xml",
                                Fixtures.reference()))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");
        byte[] zip = mvc.perform(get("/api/v1/models/" + model + "/export?fmt=oef").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Archi-Loss-Count", "9"))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] xml = unzip(zip).entrySet().stream().filter(e -> e.getKey().endsWith(".xml")).findFirst()
                .orElseThrow().getValue();
        assertThat(SCHEMA.violation(xml)).isEmpty();
    }

    private String imported(String fixture) throws Exception {
        return read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", fixture + ".archimate", "application/xml",
                                Fixtures.fixture(fixture)))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");
    }

    static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), in.readAllBytes());
            }
        }
        return files;
    }
}
