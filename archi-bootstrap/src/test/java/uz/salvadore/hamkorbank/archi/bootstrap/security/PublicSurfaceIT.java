package uz.salvadore.hamkorbank.archi.bootstrap.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.web.RequestCorrelationFilter;

/** Что открыто без токена: статика SPA, адрес входа и пробы; всё прочее — отказ доступа. */
class PublicSurfaceIT extends ApiTest {

    @Test
    @DisplayName("FR-28: без токена открыты статика, /ui-config и пробы живучести")
    void publicListIsOpen() throws Exception {
        for (String path : new String[] {"/", "/index.html", "/assets/app.js", "/api/v1/ui-config",
                "/actuator/health", "/actuator/health/liveness", "/actuator/info"}) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertTrue(status != 401 && status != 403, path + " без токена ответил " + status);
        }
    }

    @Test
    @DisplayName("FR-28: адрес вне открытого списка без токена — отказ доступа, а не обработка")
    void everythingElseIsClosed() throws Exception {
        mvc.perform(get("/internal/anything")).andExpect(status().isForbidden());
        mvc.perform(post("/")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/models")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(architect())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADR-0022: отказ цепочки безопасности несёт X-Trace-Id — тот же, что из traceparent")
    void securityRefusalCarriesTraceId() throws Exception {
        String trace = "4bf92f3577b34da6a3ce929d0e0e4736";
        mvc.perform(get("/api/v1/models").header("traceparent", "00-" + trace + "-00f067aa0ba902b7-01"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestCorrelationFilter.TRACE_HEADER, trace));
        mvc.perform(get("/actuator/prometheus").with(architect()))
                .andExpect(status().isForbidden())
                .andExpect(header().exists(RequestCorrelationFilter.TRACE_HEADER));
    }
}
