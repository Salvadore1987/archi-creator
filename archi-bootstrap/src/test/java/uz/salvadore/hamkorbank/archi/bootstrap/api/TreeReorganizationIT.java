package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;

/** Переименование, перенос и групповое удаление объектов дерева модели. */
class TreeReorganizationIT extends ApiTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Test
    @DisplayName("UC-MDL-006: переименование элемента в дереве — 204, новое имя видно в дереве модели")
    void renameChangesName() throws Exception {
        UUID model = lockedModel(architect(), "Переименование");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");

        mvc.perform(patch("/api/v1/models/" + model + "/tree/" + crm).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"CRM 2.0\"}"))
                .andExpect(status().isNoContent());

        assertThat(elementField(model, crm, "name")).containsExactly("CRM 2.0");
    }

    @Test
    @DisplayName("FR-28: наблюдатель не переименовывает объект дерева — 403 MDL_ACCESS_DENIED")
    void renameByViewerIsDenied() throws Exception {
        UUID model = lockedModel(architect(), "Переименование наблюдателем");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");

        mvc.perform(patch("/api/v1/models/" + model + "/tree/" + crm).with(viewer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Чужое\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ModelingCodes.ACCESS_DENIED));
    }

    @Test
    @DisplayName("Переименование неизвестного объекта дерева — 404 MDL_NOT_FOUND")
    void renameUnknownItemIsNotFound() throws Exception {
        UUID model = lockedModel(architect(), "Переименование пустоты");

        mvc.perform(patch("/api/v1/models/" + model + "/tree/" + IDS.generate()).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Никто\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ModelingCodes.NOT_FOUND));
    }

    @Test
    @DisplayName("INV-MDL-006: переименование без блокировки модели — 409")
    void renameWithoutLockIsConflict() throws Exception {
        UUID model = lockedModel(architect(), "Переименование без блокировки");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");
        release(model);

        mvc.perform(patch("/api/v1/models/" + model + "/tree/" + crm).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"CRM 2.0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ModelingCodes.LOCK_REQUIRED));
    }

    @Test
    @DisplayName("UC-MDL-006: перенос элементов в папку — 204, у элементов новая папка")
    void moveChangesFolder() throws Exception {
        UUID model = lockedModel(architect(), "Перенос");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");
        UUID abs = element(architect(), model, "ApplicationComponent", "АБС");
        UUID folder = folder(model, crm, "Фронт-офис");

        mvc.perform(post("/api/v1/models/" + model + "/tree/move").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(move(folder, crm, abs)))
                .andExpect(status().isNoContent());

        assertThat(elementField(model, crm, "folderId")).containsExactly(folder.toString());
        assertThat(elementField(model, abs, "folderId")).containsExactly(folder.toString());
    }

    @Test
    @DisplayName("FR-28: наблюдатель не переносит объекты дерева — 403 MDL_ACCESS_DENIED")
    void moveByViewerIsDenied() throws Exception {
        UUID model = lockedModel(architect(), "Перенос наблюдателем");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");
        UUID folder = folder(model, crm, "Фронт-офис");

        mvc.perform(post("/api/v1/models/" + model + "/tree/move").with(viewer())
                        .contentType(MediaType.APPLICATION_JSON).content(move(folder, crm)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ModelingCodes.ACCESS_DENIED));
    }

    @Test
    @DisplayName("Перенос в неизвестную модель — 404 MDL_NOT_FOUND")
    void moveInUnknownModelIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/models/" + IDS.generate() + "/tree/move").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(move(IDS.generate(), IDS.generate())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ModelingCodes.NOT_FOUND));
    }

    @Test
    @DisplayName("INV-MDL-009: перенос элемента в папку чужого слоя — 422, папка элемента прежняя")
    void moveAcrossRootsIsRejected() throws Exception {
        UUID model = lockedModel(architect(), "Перенос между слоями");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");
        UUID client = element(architect(), model, "BusinessActor", "Клиент");
        UUID businessFolder = folder(model, client, "Клиенты");
        List<String> before = elementField(model, crm, "folderId");

        mvc.perform(post("/api/v1/models/" + model + "/tree/move").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(move(businessFolder, crm)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ModelingCodes.FOLDER_TREE));

        assertThat(elementField(model, crm, "folderId")).isEqualTo(before);
    }

    @Test
    @DisplayName("UC-MDL-006: групповое удаление — 204, объектов нет в дереве модели")
    void deleteRemovesItems() throws Exception {
        UUID model = lockedModel(architect(), "Групповое удаление");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");
        UUID abs = element(architect(), model, "ApplicationComponent", "АБС");
        UUID kept = element(architect(), model, "ApplicationComponent", "Остаётся");

        mvc.perform(post("/api/v1/models/" + model + "/tree/delete").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(items(crm, abs)))
                .andExpect(status().isNoContent());

        MvcResult tree = mvc.perform(get("/api/v1/models/" + model).with(viewer()))
                .andExpect(status().isOk()).andReturn();
        List<String> left = read(tree, "$.elements[*].id");
        assertThat(left).containsExactly(kept.toString());
    }

    @Test
    @DisplayName("INV-MDL-004: группа с элементом, у которого есть связь, — 409, не удаляется ничего")
    void deleteWithRelationshipIsAllOrNothing() throws Exception {
        UUID model = lockedModel(architect(), "Удаление с отказом");
        UUID component = element(architect(), model, "ApplicationComponent", "АБС");
        UUID actor = element(architect(), model, "BusinessActor", "Клиент");
        UUID lonely = element(architect(), model, "ApplicationComponent", "Одиночка");
        mvc.perform(post("/api/v1/models/" + model + "/relationships").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archiType\":\"archimate:ServingRelationship\",\"sourceId\":\"" + component
                                + "\",\"targetId\":\"" + actor + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/models/" + model + "/tree/delete").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(items(lonely, component)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ModelingCodes.RELATIONSHIP_ENDS));

        MvcResult tree = mvc.perform(get("/api/v1/models/" + model).with(viewer()))
                .andExpect(status().isOk()).andReturn();
        List<String> left = read(tree, "$.elements[*].id");
        assertThat(left).hasSize(3);
    }

    @Test
    @DisplayName("FR-28: наблюдатель не удаляет объекты дерева — 403 MDL_ACCESS_DENIED")
    void deleteByViewerIsDenied() throws Exception {
        UUID model = lockedModel(architect(), "Удаление наблюдателем");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");

        mvc.perform(post("/api/v1/models/" + model + "/tree/delete").with(viewer())
                        .contentType(MediaType.APPLICATION_JSON).content(items(crm)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ModelingCodes.ACCESS_DENIED));
    }

    @Test
    @DisplayName("Групповое удаление в неизвестной модели — 404 MDL_NOT_FOUND")
    void deleteInUnknownModelIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/models/" + IDS.generate() + "/tree/delete").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(items(IDS.generate())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ModelingCodes.NOT_FOUND));
    }

    @Test
    @DisplayName("INV-MDL-006: групповое удаление без блокировки модели — 409")
    void deleteWithoutLockIsConflict() throws Exception {
        UUID model = lockedModel(architect(), "Удаление без блокировки");
        UUID crm = element(architect(), model, "ApplicationComponent", "CRM");
        release(model);

        mvc.perform(post("/api/v1/models/" + model + "/tree/delete").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content(items(crm)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ModelingCodes.LOCK_REQUIRED));
    }

    /** Значение поля элемента в дереве модели — список из одного значения, если элемент есть. */
    private List<String> elementField(UUID model, UUID element, String field) throws Exception {
        MvcResult tree = mvc.perform(get("/api/v1/models/" + model).with(viewer()))
                .andExpect(status().isOk()).andReturn();
        return read(tree, "$.elements[?(@.id == '" + element + "')]." + field);
    }

    /** Новая папка внутри той, где лежит элемент. */
    private UUID folder(UUID model, UUID inFolderOf, String name) throws Exception {
        UUID parent = UUIDUtil.uuid(elementField(model, inFolderOf, "folderId").getFirst());
        return id(mvc.perform(post("/api/v1/models/" + model + "/folders").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + parent + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn());
    }

    private void release(UUID model) throws Exception {
        mvc.perform(delete("/api/v1/models/" + model + "/lock").with(architect()))
                .andExpect(status().is2xxSuccessful());
    }

    private static String move(UUID target, UUID... ids) {
        return "{\"targetFolderId\":\"" + target + "\"," + items(ids).substring(1);
    }

    private static String items(UUID... ids) {
        StringBuilder json = new StringBuilder("{\"itemIds\":[");
        for (int i = 0; i < ids.length; i++) {
            json.append(i == 0 ? "" : ",").append('"').append(ids[i]).append('"');
        }
        return json.append("]}").toString();
    }
}
