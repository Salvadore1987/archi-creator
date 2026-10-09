package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/** Идентификаторы новых объектов от клиента и ребро существующей связи — через REST и базу. */
class ClientIdsIT extends ApiTest {

    @Test
    @DisplayName("ADR-0018: id и archiId от клиента сохраняются; занятые — 409 MDL_ID_TAKEN, кривой archiId — 422")
    void clientIdsOnCreate() throws Exception {
        UUID model = lockedModel(architect(), "Клиентские id");
        UUID elementId = UUID.randomUUID();
        String body = "{\"archiType\":\"archimate:ApplicationComponent\",\"name\":\"АБС\",\"id\":\"" + elementId
                + "\",\"archiId\":\"id-client-abs\"}";

        mvc.perform(post("/api/v1/models/" + model + "/elements").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(elementId.toString()))
                .andExpect(jsonPath("$.archiId").value("id-client-abs"));

        mvc.perform(post("/api/v1/models/" + model + "/elements").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MDL_ID_TAKEN"));
        mvc.perform(post("/api/v1/models/" + model + "/views").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Вид\",\"archiId\":\"id-client-abs\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MDL_ID_TAKEN"));
        mvc.perform(post("/api/v1/models/" + model + "/elements").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:ApplicationComponent\",\"name\":\"CRM\","
                                + "\"archiId\":\"<не id>\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MDL_INVALID_INPUT"));

        UUID folderId = UUID.randomUUID();
        UUID root = UUID.fromString(column("select id::text from model_folder where model_id = ? and folder_type = 'RELATIONS'",
                String.class, model).getFirst());
        mvc.perform(post("/api/v1/models/" + model + "/folders").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + root + "\",\"name\":\"Потоки\",\"id\":\"" + folderId
                                + "\",\"archiId\":\"id-client-folder\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(folderId.toString()))
                .andExpect(jsonPath("$.archiId").value("id-client-folder"));
    }

    @Test
    @DisplayName("UC-MDL-002: ребро существующей связи — 201 с ViewEdge; концы не изображают связь — 422")
    void edgeOfExistingRelationship() throws Exception {
        UUID model = lockedModel(architect(), "Рёбра");
        UUID component = element(architect(), model, "ApplicationComponent", "АБС");
        UUID data = element(architect(), model, "DataObject", "Счёт");
        UUID relations = UUID.fromString(column(
                "select id::text from model_folder where model_id = ? and folder_type = 'RELATIONS'", String.class, model)
                .getFirst());
        UUID view = id(mvc.perform(post("/api/v1/models/" + model + "/views").with(architect())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Контекст\"}"))
                .andExpect(status().isCreated()).andReturn());
        UUID cNode = UUID.randomUUID();
        mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elementId\":\"" + component + "\",\"x\":10,\"y\":10,\"id\":\"" + cNode
                                + "\",\"archiId\":\"id-node-abs\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(cNode.toString()))
                .andExpect(jsonPath("$.archiId").value("id-node-abs"));
        UUID dNode = id(mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elementId\":\"" + data + "\",\"x\":300,\"y\":10}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elementId\":\"" + data + "\",\"x\":0,\"y\":0,\"id\":\"" + UUID.randomUUID()
                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dNode.toString()));

        UUID relationship = UUID.randomUUID();
        UUID firstEdge = UUID.randomUUID();
        mvc.perform(post("/api/v1/models/" + model + "/relationships").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:AccessRelationship\",\"sourceId\":\"" + component
                                + "\",\"targetId\":\"" + data + "\",\"id\":\"" + relationship
                                + "\",\"archiId\":\"id-access\",\"folderId\":\"" + relations
                                + "\",\"accessType\":\"READ\",\"view\":{\"viewId\":\"" + view
                                + "\",\"sourceNodeId\":\"" + cNode + "\",\"targetNodeId\":\"" + dNode
                                + "\",\"edgeId\":\"" + firstEdge + "\",\"edgeArchiId\":\"id-edge-1\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(relationship.toString()))
                .andExpect(jsonPath("$.archiId").value("id-access"))
                .andExpect(jsonPath("$.accessType").value("READ"))
                .andExpect(jsonPath("$.edgeId").value(firstEdge.toString()));

        mvc.perform(delete("/api/v1/view-nodes/" + cNode).with(architect())).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elementId\":\"" + component + "\",\"x\":10,\"y\":10,\"id\":\"" + cNode
                                + "\",\"archiId\":\"id-node-abs\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/views/" + view + "/edges").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relationshipId\":\"" + relationship + "\",\"sourceId\":\"" + dNode
                                + "\",\"targetId\":\"" + cNode + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INV-MDL-008"));

        mvc.perform(post("/api/v1/views/" + view + "/edges").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + firstEdge + "\",\"archiId\":\"id-edge-1\",\"relationshipId\":\""
                                + relationship + "\",\"sourceId\":\"" + cNode + "\",\"targetId\":\"" + dNode
                                + "\",\"bendpoints\":[{\"startX\":10,\"startY\":40,\"endX\":-150,\"endY\":40}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstEdge.toString()))
                .andExpect(jsonPath("$.archiId").value("id-edge-1"))
                .andExpect(jsonPath("$.relationshipId").value(relationship.toString()))
                .andExpect(jsonPath("$.sourceId").value(cNode.toString()))
                .andExpect(jsonPath("$.bendpoints[0].endX").value(-150));

        mvc.perform(get("/api/v1/views/" + view).with(architect()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].archiId").value("id-edge-1"));
    }
}
