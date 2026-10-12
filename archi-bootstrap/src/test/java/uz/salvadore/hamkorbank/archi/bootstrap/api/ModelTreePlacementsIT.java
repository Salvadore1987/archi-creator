package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

/** {@code ModelTree.placements} на эталонной модели — сверка с таблицами узлов и рёбер. */
class ModelTreePlacementsIT extends ApiTest {

    @Test
    @DisplayName("FR-31: GET /models/{id} отдаёт размещения — по записи на представление, без повторов")
    void treeCarriesPlacements() throws Exception {
        String model = read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "reference.archimate", "application/xml",
                                Fixtures.reference()))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");

        MvcResult tree = mvc.perform(get("/api/v1/models/" + model).with(viewer())).andExpect(status().isOk())
                .andReturn();

        List<String> views = read(tree, "$.views[*].id");
        assertEquals(views, read(tree, "$.placements[*].viewId"), "по записи на каждое представление, в его порядке");
        List<List<String>> elementIds = read(tree, "$.placements[*].elementIds");
        List<List<String>> relationshipIds = read(tree, "$.placements[*].relationshipIds");
        elementIds.forEach(ids -> assertEquals(ids.size(), new HashSet<>(ids).size(), "элемент — один раз"));
        relationshipIds.forEach(ids -> assertEquals(ids.size(), new HashSet<>(ids).size(), "связь — один раз"));

        int placedElements = jdbc.queryForObject("select count(distinct (view_id, element_id)) from view_node"
                + " where model_id = ?::uuid and element_id is not null", Integer.class, model);
        int drawnRelationships = jdbc.queryForObject("select count(distinct (view_id, relationship_id)) from view_edge"
                + " where model_id = ?::uuid and relationship_id is not null", Integer.class, model);
        assertEquals(placedElements, elementIds.stream().mapToInt(List::size).sum());
        assertEquals(drawnRelationships, relationshipIds.stream().mapToInt(List::size).sum());
    }
}
