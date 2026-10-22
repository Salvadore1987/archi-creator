package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/** Каталог типов элементов для палитры: слой, фаза и признак, правится ли тип. */
class MetamodelCatalogIT extends ApiTest {

    @Test
    @DisplayName("FR-07: каталог типов доступен наблюдателю — слой, фаза и поддержка у каждого типа")
    void viewerReadsCatalog() throws Exception {
        MvcResult catalog = mvc.perform(get("/api/v1/metamodel/elements").with(viewer()))
                .andExpect(status().isOk()).andReturn();

        List<String> types = read(catalog, "$[*].archiType");
        assertThat(types).contains("archimate:ApplicationComponent", "archimate:BusinessActor",
                "archimate:Junction").doesNotHaveDuplicates();
        List<String> layers = read(catalog, "$[?(@.archiType == 'archimate:ApplicationComponent')].layer");
        assertThat(layers).containsExactly("APPLICATION");
        List<Boolean> supported = read(catalog, "$[?(@.archiType == 'archimate:ApplicationComponent')].supported");
        assertThat(supported).containsExactly(true);
    }

    @Test
    @DisplayName("FR-08: тип вне поддержанных фаз в каталоге есть, но помечен как не правящийся")
    void unsupportedTypeIsFlagged() throws Exception {
        MvcResult catalog = mvc.perform(get("/api/v1/metamodel/elements").with(architect()))
                .andExpect(status().isOk()).andReturn();

        List<Boolean> location = read(catalog, "$[?(@.archiType == 'archimate:Location')].supported");
        assertThat(location).containsExactly(false);
    }

    @Test
    @DisplayName("FR-28: каталог типов без токена — 401")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/metamodel/elements")).andExpect(status().isUnauthorized());
    }
}
