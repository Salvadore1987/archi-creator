package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultMatcher;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

/**
 * Нагрузочный профиль: p95 ≤ 300 мс
 * на операциях редактирования, кроме ИИ и импорта; открытие модели и представления —
 * p95 ≤ 1 с. Модель — эталонная (402 элемента, 368 связей, 12 представлений):
 * каждая команда грузит агрегат целиком, и меряется именно этот размер.
 *
 * <p>Последовательно, в процессе, без сети: профиль ловит деградацию хранения и
 * сценариев (N+1, полная перезапись агрегата), а не пропускную способность стенда.
 */
class LoadProfileIT extends ApiTest {

    private static final int WARM_UP = 10;
    private static final int RUNS = 60;

    @Test
    @DisplayName("NFR-03: правка эталонной модели — p95 ≤ 300 мс; NFR-01: открытие — p95 ≤ 1 с")
    void editingReferenceModelStaysWithinBudget() throws Exception {
        String model = read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "load.archimate", "application/xml", Fixtures.reference()))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());
        List<String> views = read(mvc.perform(get("/api/v1/models/" + model).with(architect())).andReturn(),
                "$.views[*].id");
        String view = views.getFirst();
        List<String> nodes = read(mvc.perform(get("/api/v1/views/" + view).with(architect())).andReturn(),
                "$.nodes[*].id");

        Map<String, Long> p95 = new LinkedHashMap<>();
        p95.put("CreateElement", measure(i -> post("/api/v1/models/" + model + "/elements").with(architect())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archiType\":\"archimate:ApplicationComponent\",\"name\":\"Нагрузка " + i + "\"}"),
                status().isCreated()));
        p95.put("SaveViewLayout", measure(i -> put("/api/v1/views/" + view + "/layout").with(architect())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodes\":[{\"id\":\"" + nodes.get(i % nodes.size()) + "\",\"x\":" + (10 + i)
                        + ",\"y\":20,\"width\":120,\"height\":55}]}"), status().isNoContent()));
        p95.put("OpenView", measure(i -> get("/api/v1/views/" + view).with(viewer()), status().isOk()));
        p95.put("OpenModel", measure(i -> get("/api/v1/models/" + model).with(viewer()), status().isOk()));

        System.out.println("NFR-03 профиль, p95 мс: " + p95);
        assertTrue(p95.get("CreateElement") <= 300, () -> "CreateElement p95 " + p95);
        assertTrue(p95.get("SaveViewLayout") <= 300, () -> "SaveViewLayout p95 " + p95);
        assertTrue(p95.get("OpenView") <= 1000, () -> "OpenView p95 " + p95);
        assertTrue(p95.get("OpenModel") <= 1000, () -> "OpenModel p95 " + p95);
    }

    private long measure(java.util.function.IntFunction<RequestBuilder> request, ResultMatcher expected)
            throws Exception {
        for (int i = 0; i < WARM_UP; i++) {
            mvc.perform(request.apply(i)).andExpect(expected);
        }
        List<Long> millis = new ArrayList<>();
        for (int i = WARM_UP; i < WARM_UP + RUNS; i++) {
            long start = System.nanoTime();
            mvc.perform(request.apply(i)).andExpect(expected);
            millis.add((System.nanoTime() - start) / 1_000_000);
        }
        millis.sort(Long::compare);
        return millis.get((int) Math.ceil(0.95 * millis.size()) - 1);
    }
}
