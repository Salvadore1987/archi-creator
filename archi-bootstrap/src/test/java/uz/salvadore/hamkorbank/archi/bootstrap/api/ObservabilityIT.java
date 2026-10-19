package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
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
        UUID model = lockedModel(architect(), "Наблюдаемость");
        mvc.perform(MockMvcRequestBuilders
                .post("/api/v1/models/" + model + "/lock").with(as("intruder", "ARCHITECT")))
                .andExpect(status().isConflict());
        mvc.perform(multipart("/api/v1/models/import")
                .file(new MockMultipartFile("file", "f.archimate", "application/xml",
                        Fixtures.fixture("unknown_extension")))
                .with(architect())).andExpect(status().isCreated());

        mvc.perform(get("/actuator/prometheus").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("modeling_usecase_requests_total{")))
                .andExpect(content().string(containsString("usecase=\"CreateModel\"")))
                .andExpect(content().string(containsString("error_code=\"INV-MDL-006\"")))
                .andExpect(content().string(containsString("modeling_usecase_duration_seconds_bucket{")))
                .andExpect(content().string(containsString("modeling_lock_wait_seconds")))
                .andExpect(content().string(containsString("interchange_import_sessions_total{")))
                .andExpect(content().string(containsString("interchange_opaque_objects{")));
        mvc.perform(get("/actuator/prometheus").with(architect())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("§8.4: traceId берётся из traceparent и возвращается заголовком")
    void traceIdFollowsTraceparent() throws Exception {
        String trace = "4bf92f3577b34da6a3ce929d0e0e4736";
        mvc.perform(get("/api/v1/models").with(viewer()).header("traceparent", "00-" + trace + "-00f067aa0ba902b7-01"))
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
