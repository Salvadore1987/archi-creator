package uz.salvadore.hamkorbank.archi.bootstrap.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;

/** Откат модели к версии и метка версии через REST. */
class VersionRestoreLabelIT extends ApiTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Test
    @DisplayName("UC-MDL-004: откат к версии — 200 с новой версией, содержимое модели — как в старой")
    void restoreBringsBackOldContent() throws Exception {
        UUID model = modelWithTwoVersions("Откат");

        mvc.perform(post(versions(model) + "/1/restore").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Назад к пустой\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(3))
                .andExpect(jsonPath("$.comment").value("Назад к пустой"))
                .andExpect(jsonPath("$.author").value("architect-api"));

        MvcResult tree = mvc.perform(get("/api/v1/models/" + model).with(viewer()))
                .andExpect(status().isOk()).andReturn();
        List<String> elements = read(tree, "$.elements[*].id");
        assertThat(elements).isEmpty();
        MvcResult history = mvc.perform(get(versions(model)).with(viewer())).andExpect(status().isOk()).andReturn();
        List<Integer> numbers = read(history, "$[*].versionNo");
        assertThat(numbers).containsExactly(3, 2, 1);
    }

    @Test
    @DisplayName("UC-MDL-004: откат без комментария — комментарий по умолчанию, не пустой")
    void restoreWithoutBodyGetsDefaultComment() throws Exception {
        UUID model = modelWithTwoVersions("Откат без комментария");

        MvcResult restored = mvc.perform(post(versions(model) + "/1/restore").with(architect()))
                .andExpect(status().isOk()).andReturn();

        String comment = read(restored, "$.comment");
        assertThat(comment).isNotBlank();
    }

    @Test
    @DisplayName("FR-28: наблюдатель не откатывает модель — 403 MDL_ACCESS_DENIED")
    void restoreByViewerIsDenied() throws Exception {
        UUID model = modelWithTwoVersions("Откат наблюдателем");

        mvc.perform(post(versions(model) + "/1/restore").with(viewer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ModelingCodes.ACCESS_DENIED));
    }

    @Test
    @DisplayName("Откат неизвестной модели — 404 MDL_NOT_FOUND")
    void restoreUnknownModelIsNotFound() throws Exception {
        mvc.perform(post(versions(IDS.generate()) + "/1/restore").with(architect()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ModelingCodes.NOT_FOUND));
    }

    @Test
    @DisplayName("Откат к несуществующей версии — 404 MDL_NOT_FOUND")
    void restoreUnknownVersionIsNotFound() throws Exception {
        UUID model = modelWithTwoVersions("Откат в никуда");

        mvc.perform(post(versions(model) + "/99/restore").with(architect()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ModelingCodes.NOT_FOUND));
    }

    @Test
    @DisplayName("INV-MDL-006: откат без блокировки модели — 409, версий не прибавилось")
    void restoreWithoutLockIsConflict() throws Exception {
        UUID model = modelWithTwoVersions("Откат без блокировки");
        mvc.perform(delete("/api/v1/models/" + model + "/lock").with(architect()))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(post(versions(model) + "/1/restore").with(architect()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ModelingCodes.LOCK_REQUIRED));

        MvcResult history = mvc.perform(get(versions(model)).with(viewer())).andExpect(status().isOk()).andReturn();
        List<Integer> numbers = read(history, "$[*].versionNo");
        assertThat(numbers).containsExactly(2, 1);
    }

    @Test
    @DisplayName("FR-48: метка версии ставится и видна в истории, пустая — снимает её")
    void labelIsSetAndCleared() throws Exception {
        UUID model = modelWithTwoVersions("Метка");

        mvc.perform(put(versions(model) + "/1/label").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"release-1.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.label").value("release-1.0"));
        MvcResult history = mvc.perform(get(versions(model)).with(viewer())).andExpect(status().isOk()).andReturn();
        List<String> labels = read(history, "$[?(@.versionNo == 1)].label");
        assertThat(labels).containsExactly("release-1.0");

        mvc.perform(put(versions(model) + "/1/label").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").doesNotExist());
    }

    @Test
    @DisplayName("FR-48: метка не требует блокировки модели")
    void labelWithoutLockSucceeds() throws Exception {
        UUID model = modelWithTwoVersions("Метка без блокировки");
        mvc.perform(delete("/api/v1/models/" + model + "/lock").with(architect()))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(put(versions(model) + "/2/label").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("v2"));
    }

    @Test
    @DisplayName("FR-28: наблюдатель не ставит метку — 403 MDL_ACCESS_DENIED")
    void labelByViewerIsDenied() throws Exception {
        UUID model = modelWithTwoVersions("Метка наблюдателя");

        mvc.perform(put(versions(model) + "/1/label").with(viewer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"release\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ModelingCodes.ACCESS_DENIED));
    }

    @Test
    @DisplayName("Метка несуществующей версии — 404 MDL_NOT_FOUND")
    void labelUnknownVersionIsNotFound() throws Exception {
        UUID model = modelWithTwoVersions("Метка в никуда");

        mvc.perform(put(versions(model) + "/99/label").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"release\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ModelingCodes.NOT_FOUND));
    }

    @Test
    @DisplayName("Метка в неизвестной модели — 404 MDL_NOT_FOUND")
    void labelUnknownModelIsNotFound() throws Exception {
        mvc.perform(put(versions(IDS.generate()) + "/1/label").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"release\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ModelingCodes.NOT_FOUND));
    }

    /** Модель с блокировкой автора: версия 1 пустая, версия 2 — с одним элементом. */
    private UUID modelWithTwoVersions(String name) throws Exception {
        UUID model = lockedModel(architect(), name);
        element(architect(), model, "ApplicationComponent", "CRM");
        mvc.perform(post(versions(model)).with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"С элементом\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version.versionNo").value(2));
        return model;
    }

    private static String versions(UUID model) {
        return "/api/v1/models/" + model + "/versions";
    }
}
