package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.XmlEquivalence.assertXmlEquivalent;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

/**
 * Фаза 2 метамодели: Capability и остальные типы Motivation и Strategy правятся
 * наравне с фазой 1, а модели, загруженные, пока фаза 2 была opaque, получают это миграцией.
 */
class PhaseTwoIT extends ApiTest {

    private static final String FIXTURE = "capability_and_location";
    private static final String CAPABILITY = "Расчётно-кассовое обслуживание";

    @Test
    @DisplayName("FR-08, UC-MDL-002: Capability правится, создаётся и связывается; правка уходит в выгрузку без потерь")
    void capabilityIsEditedNotOnlyStored() throws Exception {
        byte[] original = Fixtures.fixture(FIXTURE);
        String model = importFixture(original);
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());
        String capability = elementId(model, CAPABILITY);

        mvc.perform(patch("/api/v1/elements/" + capability).with(architect()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"РКО\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(true));
        save(model, "переименование");

        String renamed = new String(original, StandardCharsets.UTF_8).replace(CAPABILITY, "РКО");
        assertXmlEquivalent(renamed.getBytes(StandardCharsets.UTF_8), export(model));

        UUID created = element(architect(), UUIDUtil.uuid(model), "Capability", "Мгновенные платежи");
        mvc.perform(post("/api/v1/models/" + model + "/relationships").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:RealizationRelationship\",\"sourceId\":\""
                                + elementId(model, "АБС") + "\",\"targetId\":\"" + created + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/models/" + model + "/relationships").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:AssignmentRelationship\",\"sourceId\":\""
                                + created + "\",\"targetId\":\"" + elementId(model, "АБС") + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RELATION_NOT_PERMITTED"));
        save(model, "новая способность");

        String exported = new String(export(model), StandardCharsets.UTF_8);
        assertTrue(exported.contains("xsi:type=\"archimate:Capability\" name=\"Мгновенные платежи\""), exported);
        assertTrue(exported.contains("<property key=\"level\" value=\"1\"/>"), "свойство импортированной способности");
    }

    @Test
    @DisplayName("FR-08: тип фазы 3 и Location по-прежнему не правятся — 422")
    void phaseThreeStaysOpaque() throws Exception {
        String model = importFixture(Fixtures.fixture(FIXTURE));
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());

        for (String name : List.of("Внедрение СМП", "Ташкент")) {
            mvc.perform(patch("/api/v1/elements/" + elementId(model, name)).with(architect())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Другое\"}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("MDL_TYPE_NOT_EDITABLE"));
        }
        mvc.perform(post("/api/v1/models/" + model + "/elements").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:WorkPackage\",\"name\":\"Новый пакет\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MDL_TYPE_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("FR-08: миграция V5 делает поддержанными элементы фазы 2, загруженные до этапа 6; остаток не трогает")
    void migrationUpgradesModelsImportedBeforePhaseTwo() throws Exception {
        String model = importFixture(Fixtures.fixture(FIXTURE));
        UUID modelId = UUIDUtil.uuid(model);
        Map<String, String> residueBefore = residue(modelId);
        // Так строки фазы 2 выглядели после импорта, пока фаза была opaque.
        jdbc.update("""
                update element set supported = false
                where model_id = ? and layer in ('MOTIVATION', 'STRATEGY')
                """, modelId);

        jdbc.execute(migration("db/migration/V5__phase_two_supported.sql"));

        Map<String, Boolean> supported = jdbc.queryForList(
                        "select name, supported from element where model_id = ?", modelId).stream()
                .collect(Collectors.toMap(r -> (String) r.get("name"), r -> (Boolean) r.get("supported")));
        assertEquals(true, supported.get(CAPABILITY));
        assertEquals(true, supported.get("Платёж за 10 секунд"));
        assertEquals(true, supported.get("Процессинговый центр"));
        assertEquals(false, supported.get("Внедрение СМП"));
        assertEquals(false, supported.get("Банкомат"));
        assertEquals(false, supported.get("Ташкент"));
        assertEquals(residueBefore, residue(modelId));

        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/elements/" + elementId(model, CAPABILITY)).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"documentation\":\"Уровень 1\"}"))
                .andExpect(status().isOk());
    }

    private String importFixture(byte[] content) throws Exception {
        return read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", FIXTURE + ".archimate", "application/xml", content))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");
    }

    private String elementId(String model, String name) throws Exception {
        List<String> ids = read(mvc.perform(get("/api/v1/models/" + model).with(architect())).andReturn(),
                "$.elements[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    private void save(String model, String comment) throws Exception {
        mvc.perform(post("/api/v1/models/" + model + "/versions").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"" + comment + "\"}"))
                .andExpect(status().isCreated());
    }

    private byte[] export(String model) throws Exception {
        return mvc.perform(get("/api/v1/models/" + model + "/export").with(viewer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private Map<String, String> residue(UUID model) {
        return jdbc.queryForList("select archi_id, raw_xml from element where model_id = ?", model).stream()
                .collect(Collectors.toMap(r -> (String) r.get("archi_id"), r -> (String) r.get("raw_xml")));
    }

    private static String migration(String path) throws IOException {
        try (InputStream in = PhaseTwoIT.class.getClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
