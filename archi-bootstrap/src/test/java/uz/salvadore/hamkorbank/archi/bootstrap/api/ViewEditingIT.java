package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.impl.UUIDUtil;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/** Правка представления — группы, заметки, стиль, рёбра — через REST, базу и выгрузку. */
class ViewEditingIT extends ApiTest {

    /** Идентификаторы тестовых объектов — версии 7, из той же библиотеки, что у приложения. */
    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Test
    @DisplayName("UC-MDL-008: группа, заметка и стиль пишутся из столбцов; удалённое ребро не выгружается, связь — да")
    void annotationsStyleAndEdgeRemovalSurviveExport() throws Exception {
        UUID model = lockedModel(architect(), "Правка представления");
        UUID component = element(architect(), model, "ApplicationComponent", "АБС");
        UUID actor = element(architect(), model, "BusinessActor", "Клиент");
        UUID view = id(mvc.perform(post("/api/v1/models/" + model + "/views").with(architect())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Контекст\"}"))
                .andExpect(status().isCreated()).andReturn());

        UUID group = id(mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"GROUP\",\"label\":\"Ядро\",\"x\":0,\"y\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("GROUP"))
                .andExpect(jsonPath("$.width").value(400))
                .andReturn());
        mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"NOTE\",\"content\":\"Строка 1\\nСтрока 2\",\"x\":500,\"y\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Строка 1\nСтрока 2"));
        mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"GROUP\",\"elementId\":\"" + component + "\",\"x\":0,\"y\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MDL_INVALID_INPUT"));
        mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"SKETCH\",\"x\":0,\"y\":0}"))
                .andExpect(status().isUnprocessableContent());

        UUID cNode = id(mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elementId\":\"" + component + "\",\"x\":20,\"y\":30,\"parentId\":\"" + group
                                + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        UUID aNode = id(mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elementId\":\"" + actor + "\",\"x\":20,\"y\":300}"))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(patch("/api/v1/view-nodes/" + cNode).with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"style\":{\"fillColor\":\"#ff8800\",\"fontColor\":\"#112233\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style.fillColor").value("#ff8800"));
        mvc.perform(patch("/api/v1/view-nodes/" + cNode).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"style\":{\"fillColor\":\"красный\"}}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(patch("/api/v1/view-nodes/" + group).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Ядро банка\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Ядро банка"));

        String serving = mvc.perform(post("/api/v1/models/" + model + "/relationships").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:ServingRelationship\",\"sourceId\":\"" + component
                                + "\",\"targetId\":\"" + actor + "\",\"view\":{\"viewId\":\"" + view
                                + "\",\"sourceNodeId\":\"" + cNode + "\",\"targetNodeId\":\"" + aNode + "\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID edge = UUIDUtil.uuid(com.jayway.jsonpath.JsonPath.<String>read(serving, "$.edgeId"));
        mvc.perform(patch("/api/v1/view-edges/" + edge).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"style\":{\"lineColor\":\"#00aa00\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style.lineColor").value("#00aa00"));

        saveVersion(model);
        String withEdge = export(model);
        assertTrue(withEdge.contains("xsi:type=\"archimate:Group\"") && withEdge.contains("name=\"Ядро банка\""),
                withEdge);
        assertTrue(withEdge.contains("<content>Строка 1\nСтрока 2</content>"), "заметка с переводом строки");
        assertTrue(withEdge.contains("fillColor=\"#ff8800\"") && withEdge.contains("fontColor=\"#112233\""),
                "стиль узла из столбцов");
        assertTrue(withEdge.contains("lineColor=\"#00aa00\""), "стиль ребра из столбцов");

        mvc.perform(delete("/api/v1/view-edges/" + edge).with(architect())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/view-edges/" + edge).with(architect())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/views/" + view).with(architect()))
                .andExpect(jsonPath("$.edges.length()").value(0))
                .andExpect(jsonPath("$.nodes.length()").value(4));
        saveVersion(model);
        String withoutEdge = export(model);
        assertTrue(!withoutEdge.contains("archimate:Connection"), "ребро ушло с представления");
        assertTrue(withoutEdge.contains("archimate:ServingRelationship"), "связь осталась в модели");
    }

    @Test
    @DisplayName("UI-004: узел вкладывается в контейнер и выносится обратно; цикл — 422; связь вложения помечена")
    void nodeMovesIntoContainer() throws Exception {
        UUID model = lockedModel(architect(), "Вложенность");
        UUID server = element(architect(), model, "Node", "Сервер");
        UUID dbms = element(architect(), model, "SystemSoftware", "СУБД");
        UUID view = id(mvc.perform(post("/api/v1/models/" + model + "/views").with(architect())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Развёртывание\"}"))
                .andExpect(status().isCreated()).andReturn());
        UUID outer = id(mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elementId\":\"" + server + "\",\"x\":0,\"y\":0,\"width\":400,\"height\":300}"))
                .andExpect(status().isCreated()).andReturn());
        UUID inner = id(mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elementId\":\"" + dbms + "\",\"x\":500,\"y\":40}"))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(get("/api/v1/metamodel/relations").param("source", "archimate:Node")
                        .param("target", "archimate:SystemSoftware").with(architect()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nesting == true)].type")
                        .value(org.hamcrest.Matchers.contains("COMPOSITION")));

        mvc.perform(put("/api/v1/view-nodes/" + inner + "/parent").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + outer + "\",\"x\":20,\"y\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(outer.toString()))
                .andExpect(jsonPath("$.x").value(20));
        mvc.perform(put("/api/v1/view-nodes/" + outer + "/parent").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + inner + "\",\"x\":0,\"y\":0}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INV-MDL-008"));

        saveVersion(model);
        String nested = export(model);
        int outerAt = nested.indexOf("archimate:DiagramObject");
        int innerAt = nested.indexOf("archimate:DiagramObject", outerAt + 1);
        assertTrue(innerAt > outerAt && nested.indexOf("</child>", outerAt) > innerAt,
                "вложенный узел — дочерний элемент контейнера в файле");

        mvc.perform(put("/api/v1/view-nodes/" + inner + "/parent").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":520,\"y\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").doesNotExist());
    }

    @Test
    @DisplayName("ADR-0019: авторазметка отвечает предложением по графу из браузера и ничего не пишет")
    void autoLayoutProposesWithoutWriting() throws Exception {
        UUID model = lockedModel(architect(), "Авторазметка");
        UUID component = element(architect(), model, "ApplicationComponent", "АБС");
        UUID view = id(mvc.perform(post("/api/v1/models/" + model + "/views").with(architect())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Слои\"}"))
                .andExpect(status().isCreated()).andReturn());
        UUID appNode = id(mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elementId\":\"" + component + "\",\"x\":0,\"y\":0}"))
                .andExpect(status().isCreated()).andReturn());
        UUID unsaved = IDS.generate();
        String graph = "{\"strategy\":\"layered\",\"nodes\":[{\"id\":\"" + appNode
                + "\",\"conceptType\":\"archimate:ApplicationComponent\",\"x\":0,\"y\":0,\"width\":120,\"height\":55},"
                + "{\"id\":\"" + unsaved + "\",\"conceptType\":\"archimate:BusinessProcess\",\"x\":0,\"y\":0,"
                + "\"width\":120,\"height\":55}],\"edges\":[{\"id\":\"" + IDS.generate() + "\",\"sourceId\":\""
                + appNode + "\",\"targetId\":\"" + unsaved + "\"}]}";

        MvcResult proposal = mvc.perform(post("/api/v1/views/" + view + "/auto-layout").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(graph))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.edges[0].bendpoints.length()").value(0))
                .andReturn();
        int businessY = ((Number) read(proposal, "$.nodes[?(@.id == '" + unsaved + "')].y", 0)).intValue();
        int appY = ((Number) read(proposal, "$.nodes[?(@.id == '" + appNode + "')].y", 0)).intValue();
        assertTrue(businessY < appY, "бизнес выше приложений");

        mvc.perform(get("/api/v1/views/" + view).with(architect()))
                .andExpect(jsonPath("$.nodes.length()").value(1))
                .andExpect(jsonPath("$.nodes[0].x").value(0))
                .andExpect(jsonPath("$.nodes[0].y").value(0));
        mvc.perform(post("/api/v1/views/" + view + "/auto-layout").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(graph.replace("layered", "spiral")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MDL_INVALID_INPUT"));
        mvc.perform(post("/api/v1/views/" + view + "/auto-layout").with(viewer())
                        .contentType(MediaType.APPLICATION_JSON).content(graph))
                .andExpect(status().isForbidden());
    }

    private static Object read(org.springframework.test.web.servlet.MvcResult result, String path, int index)
            throws Exception {
        java.util.List<Object> values = read(result, path);
        return values.get(index);
    }

    private void saveVersion(UUID model) throws Exception {
        mvc.perform(post("/api/v1/models/" + model + "/versions").with(architect())
                        .header("Idempotency-Key", IDS.generate().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is2xxSuccessful());
    }

    private String export(UUID model) throws Exception {
        return new String(mvc.perform(get("/api/v1/models/" + model + "/export").with(architect()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
