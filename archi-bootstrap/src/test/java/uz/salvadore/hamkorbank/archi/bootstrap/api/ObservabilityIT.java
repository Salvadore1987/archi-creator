package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

/** Метрики use case'ов видны в /actuator/prometheus, запрос несёт traceId. */
class ObservabilityIT extends ApiTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Test
    @DisplayName("§8.4: метрики use case'ов и импорта — в Prometheus, код инварианта — в error_code")
    void useCaseMetricsReachPrometheus() throws Exception {
        String before = scrape();
        UUID model = lockedModel(architect(), "Наблюдаемость");
        mvc.perform(MockMvcRequestBuilders
                .post("/api/v1/models/" + model + "/lock").with(as("intruder", "ARCHITECT")))
                .andExpect(status().isConflict());
        String imported = read(mvc.perform(multipart("/api/v1/models/import")
                .file(new MockMultipartFile("file", "f.archimate", "application/xml",
                        Fixtures.fixture("unknown_extension")))
                .with(architect())).andExpect(status().isCreated()).andReturn(), "$.modelId");

        String after = scrape();
        // Контекст общий для классов: значения растут и от чужих тестов, поэтому сравнивается приращение.
        assertThat(sum(after, "modeling_usecase_requests_total{", "usecase=\"CreateModel\""))
                .isGreaterThan(sum(before, "modeling_usecase_requests_total{", "usecase=\"CreateModel\""));
        assertThat(sum(after, "modeling_usecase_errors_total{", "error_code=\"INV-MDL-006\""))
                .isGreaterThan(sum(before, "modeling_usecase_errors_total{", "error_code=\"INV-MDL-006\""));
        assertThat(sum(after, "modeling_usecase_duration_seconds_count{", "usecase=\"CreateModel\""))
                .isGreaterThan(sum(before, "modeling_usecase_duration_seconds_count{", "usecase=\"CreateModel\""));
        assertThat(sum(after, "modeling_lock_wait_seconds_count", ""))
                .isGreaterThan(sum(before, "modeling_lock_wait_seconds_count", ""));
        assertThat(sum(after, "interchange_import_sessions_total{", ""))
                .isGreaterThan(sum(before, "interchange_import_sessions_total{", ""));
        assertThat(after).contains("interchange_opaque_objects{").contains("model_id=\"" + imported + "\"");
        mvc.perform(get("/actuator/prometheus").with(architect())).andExpect(status().isForbidden());
    }

    private String scrape() throws Exception {
        return mvc.perform(get("/actuator/prometheus").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Сумма значений рядов метрики, в метках которых есть {@code label}. */
    private static double sum(String scrape, String metric, String label) {
        return scrape.lines().filter(l -> l.startsWith(metric) && l.contains(label))
                .mapToDouble(l -> Double.parseDouble(l.substring(l.lastIndexOf(' ') + 1))).sum();
    }

    @Test
    @DisplayName("§8.4: traceId берётся из traceparent и возвращается заголовком")
    void traceIdFollowsTraceparent() throws Exception {
        String trace = "4bf92f3577b34da6a3ce929d0e0e4736";
        mvc.perform(get("/api/v1/models").with(viewer()).header("traceparent", "00-" + trace + "-00f067aa0ba902b7-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", trace));
    }

    @Test
    @DisplayName("ADR-0022: отказ problem+json тоже несёт X-Trace-Id; без traceparent — новый, 32 hex")
    void problemCarriesGeneratedTraceId() throws Exception {
        mvc.perform(get("/api/v1/models/" + IDS.generate()).with(viewer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("X-Trace-Id", matchesPattern("^[0-9a-f]{32}$")));
    }
}
