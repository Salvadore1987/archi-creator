package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.XmlEquivalence.assertXmlEquivalent;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

/** Подпись группы и текст заметки — в столбцах узла, видны в payload и переживают выгрузку. */
class ViewNodeTextIT extends ApiTest {

    private static final String NOTE = "id-71ceb465a7046f0af529195ab44377db";
    private static final String LEGEND = "Легенда:\nкрасное — платёжный поток;\nзелёное — обслуживание клиента.";

    @Test
    @DisplayName("ADR-0017: текст заметки — в столбце content и в GET /views/{id}; импорт → экспорт даёт тот же текст")
    void noteContentIsTypedAndSurvivesExport() throws Exception {
        byte[] original = Fixtures.fixture("styled_objects");
        String model = importModel("styled_objects.archimate", original);

        assertEquals(List.of(LEGEND), column("select content from view_node where model_id = ?::uuid and archi_id = ?",
                String.class, model, NOTE));
        List<String> views = read(mvc.perform(get("/api/v1/models/" + model).with(viewer()))
                .andExpect(status().isOk()).andReturn(), "$.views[*].id");
        String view = views.getFirst();
        var payload = mvc.perform(get("/api/v1/views/" + view).with(viewer())).andExpect(status().isOk()).andReturn();
        assertEquals(List.of(LEGEND), read(payload, "$.nodes[?(@.archiId == '" + NOTE + "')].content"));
        assertEquals(List.of("NOTE"), read(payload, "$.nodes[?(@.archiId == '" + NOTE + "')].kind"));

        byte[] exported = mvc.perform(get("/api/v1/models/" + model + "/export").with(viewer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertXmlEquivalent(original, exported);
        assertTrue(new String(exported, StandardCharsets.UTF_8).contains("<content>" + LEGEND + "</content>"),
                "переводы строк заметки на месте");
    }

    @Test
    @DisplayName("ADR-0017: подпись группы — в столбце label и в GET /views/{id}; у узла элемента её нет")
    void groupLabelIsTyped() throws Exception {
        String model = importModel("Hamkorbank_AS_IS_strict.archimate", Fixtures.reference());

        List<String> view = column("select view_id::text from view_node where model_id = ?::uuid and label = ?"
                + " and kind = 'GROUP' order by archi_id", String.class, model, "Каналы");
        assertEquals(1, view.size(), "группа «Каналы» эталона — подпись в столбце");
        var payload = mvc.perform(get("/api/v1/views/" + view.getFirst()).with(viewer()))
                .andExpect(status().isOk()).andReturn();
        assertEquals(List.of("GROUP"), read(payload, "$.nodes[?(@.label == 'Каналы')].kind"));
        List<Object> objectLabels = read(payload, "$.nodes[?(@.kind == 'DIAGRAM_OBJECT')].label");
        assertTrue(objectLabels.stream().allMatch(java.util.Objects::isNull), "у узла над элементом подпись — имя элемента");
    }

    private String importModel(String fileName, byte[] file) throws Exception {
        return read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", fileName, "application/xml", file))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");
    }
}
