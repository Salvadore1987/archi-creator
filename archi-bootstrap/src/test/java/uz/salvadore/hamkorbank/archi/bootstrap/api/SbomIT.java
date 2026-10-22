package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;

/** Состав зависимостей приложения отдаёт Actuator, и только администратору. */
class SbomIT extends ApiTest {

    @Test
    @DisplayName("/actuator/sbom/application — CycloneDX приложения с его зависимостями, без тестовых")
    void applicationSbomIsServed() throws Exception {
        mvc.perform(get("/actuator/sbom/application").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bomFormat").value("CycloneDX"))
                .andExpect(jsonPath("$.metadata.component.name").value("archi-bootstrap"))
                .andExpect(jsonPath("$.components[*].name", Matchers.hasItem("tomcat-embed-core")))
                .andExpect(jsonPath("$.components[*].name", Matchers.not(Matchers.hasItem("junit-jupiter-api"))));
    }

    @Test
    @DisplayName("состав зависимостей — не для архитектора: по нему ищут уязвимую версию")
    void sbomIsAdministrative() throws Exception {
        mvc.perform(get("/actuator/sbom/application").with(architect())).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/sbom")).andExpect(status().isUnauthorized());
    }
}
