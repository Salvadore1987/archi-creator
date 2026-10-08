package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.XmlEquivalence.assertXmlEquivalent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

/**
 * Выход этапа 2 (§12): «импорт файла → сохранение в БД → экспорт через API» даёт
 * исходный файл, {@code assertXmlEquivalent} зелёный (NFR-05, INV-IXC-005).
 */
class RoundTripApiIT extends ApiTest {

    @Test
    @DisplayName("§12, INV-IXC-005: импорт → БД → экспорт через API даёт исходный файл")
    void importStoreExportThroughApi() throws Exception {
        byte[] original = Fixtures.reference();

        String modelId = read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "Hamkorbank_AS_IS_strict.archimate", "application/xml",
                                original))
                        .header("Idempotency-Key", "roundtrip-" + UUID.randomUUID())
                        .with(architect()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.versionNo").value(1))
                .andReturn(), "$.modelId");

        byte[] exported = mvc.perform(get("/api/v1/models/" + modelId + "/export").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Archi-Version-No", "1"))
                .andReturn().getResponse().getContentAsByteArray();

        assertXmlEquivalent(original, exported);
    }

    @Test
    @DisplayName("INV-IXC-008: выгружается зафиксированная версия, правка видна после сохранения")
    void exportFollowsCommittedVersion() throws Exception {
        byte[] original = Fixtures.fixture("styled_objects");
        String modelId = read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "styled.archimate", "application/xml", original))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");
        mvc.perform(post("/api/v1/models/" + modelId + "/lock").with(architect())).andExpect(status().isOk());
        java.util.List<String> ids = read(mvc.perform(get("/api/v1/models/" + modelId).with(architect())).andReturn(),
                "$.elements[?(@.name == 'АБС')].id");
        String element = ids.getFirst();
        mvc.perform(patch("/api/v1/elements/" + element).with(architect()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"АБС «Ядро»\"}")).andExpect(status().isOk());

        byte[] beforeSave = mvc.perform(get("/api/v1/models/" + modelId + "/export").with(viewer()))
                .andReturn().getResponse().getContentAsByteArray();
        assertXmlEquivalent(original, beforeSave);

        mvc.perform(post("/api/v1/models/" + modelId + "/versions").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"переименование\"}"))
                .andExpect(status().isCreated());
        String afterSave = new String(mvc.perform(get("/api/v1/models/" + modelId + "/export").with(viewer()))
                .andExpect(header().string("X-Archi-Version-No", "2"))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertEquals(true, afterSave.contains("name=\"АБС «Ядро»\""));
        mvc.perform(get("/api/v1/models/" + modelId + "/export?version=1").with(viewer()))
                .andExpect(header().string("X-Archi-Version-No", "1"));
    }
}
