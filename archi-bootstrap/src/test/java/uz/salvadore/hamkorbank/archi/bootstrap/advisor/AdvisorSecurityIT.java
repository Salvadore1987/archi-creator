package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AdvisorSecurityIT extends AdvisorApiTest {

    @Test
    @DisplayName("INV-ADV-009: ключ Anthropic уходит только во внешний API — не в ответ, не в журнал, не в метрики")
    void apiKeyNeverAppearsInResponseOrLog(CapturedOutput output) throws Exception {
        UUID model = lockedModel(architect(), "Секрет");

        Response ok = ask("review", architect(), "secret-1", "{\"modelId\":\"" + model + "\"}");
        anthropic.failWith(401);
        Response failed = ask("review", architect(), "secret-2", "{\"modelId\":\"" + model + "\"}");
        String metrics = mvc.perform(get("/actuator/prometheus").with(admin())).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(anthropic.requests().getFirst().apiKey()).isEqualTo(API_KEY);
        assertThat(ok.body()).doesNotContain(API_KEY);
        assertThat(failed.body()).doesNotContain(API_KEY);
        assertThat(metrics).doesNotContain(API_KEY);
        assertThat(output.getAll()).doesNotContain(API_KEY);
    }

    @Test
    @DisplayName("INV-ADV-009: содержимое контекста и описание пользователя не попадают в журнал")
    void promptContentIsNotLogged(CapturedOutput output) throws Exception {
        UUID model = lockedModel(architect(), "Ландшафт");
        element(architect(), model, "ApplicationComponent", "Секретная система ЦБ-7781");
        anthropic.failWith(500);

        ask("plan", architect(), "secret-3",
                "{\"modelId\":\"" + model + "\",\"prompt\":\"Описание закрытого контура 5521\"}");
        anthropic.reset();
        ask("plan", architect(), "secret-4",
                "{\"modelId\":\"" + model + "\",\"prompt\":\"Описание закрытого контура 5521\"}");

        assertThat(anthropic.requests().getFirst().body()).contains("Секретная система ЦБ-7781");
        assertThat(output.getAll()).doesNotContain("Секретная система ЦБ-7781").doesNotContain("5521");
    }
}
