package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/** Правка представления — группы, заметки, стиль, рёбра — через REST, базу и выгрузку. */
class ViewEditingIT extends ApiTest {

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
        UUID edge = UUID.fromString(com.jayway.jsonpath.JsonPath.read(serving, "$.edgeId"));
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

    private void saveVersion(UUID model) throws Exception {
        mvc.perform(post("/api/v1/models/" + model + "/versions").with(architect())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is2xxSuccessful());
    }

    private String export(UUID model) throws Exception {
        return new String(mvc.perform(get("/api/v1/models/" + model + "/export").with(architect()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
