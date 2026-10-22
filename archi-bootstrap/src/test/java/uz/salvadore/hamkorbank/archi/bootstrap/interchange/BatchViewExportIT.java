package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

class BatchViewExportIT extends ApiTest {

    @Test
    @DisplayName("INV-IXC-008: пакет картинок собран из одной версии — правка после неё не видна ни в одном файле")
    void allImagesComeFromOneVersion() throws Exception {
        String model = read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "reference.archimate", "application/xml",
                                Fixtures.reference()))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());
        List<String> iabs = read(mvc.perform(get("/api/v1/models/" + model).with(architect()))
                .andExpect(status().isOk()).andReturn(),
                "$.elements[?(@.name == 'IABS')].id");
        mvc.perform(patch("/api/v1/elements/" + iabs.getFirst()).with(architect())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"IABS Новый\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/models/" + model + "/versions").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"переименование\"}"))
                .andExpect(status().isCreated());

        Map<String, String> first = unzip(mvc.perform(get("/api/v1/models/" + model + "/views/export?version=1")
                        .with(viewer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("X-Archi-Version-No", "1"))
                .andReturn().getResponse().getContentAsByteArray());

        assertThat(first).hasSize(12 + 1).containsKey("loss-report.json")
                .containsKey("01-kontekst-as-is-kak-narisovano-v-visio.f11fbcb8.svg");
        assertThat(first.get("loss-report.json")).contains("\"sourceVersionNo\":1", "\"format\":\"SVG\"");
        List<String> images = first.keySet().stream().filter(n -> n.endsWith(".svg")).toList();
        assertThat(images.stream().filter(n -> first.get(n).contains(">IABS<"))).hasSize(4);
        assertThat(images).allSatisfy(n -> assertThat(first.get(n)).doesNotContain("Новый"));

        Map<String, String> latest = unzip(mvc.perform(get("/api/v1/models/" + model + "/views/export")
                        .with(viewer())).andExpect(status().isOk())
                .andExpect(header().string("X-Archi-Version-No", "2"))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(latest.values().stream().filter(svg -> svg.contains("Новый<")))
                .as("перенос по ширине допустим").hasSize(4);
        assertThat(latest.values()).noneMatch(svg -> svg.contains(">IABS<") && !svg.contains("Новый<"));
    }

    private static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
