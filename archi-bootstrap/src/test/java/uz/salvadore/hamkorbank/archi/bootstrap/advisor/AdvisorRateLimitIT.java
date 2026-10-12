package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Частота — со своим узким окном автора: остальные тесты помощника делят широкое. */
@TestPropertySource(properties = "archi.ai.user-requests-per-minute=5")
class AdvisorRateLimitIT extends AdvisorApiTest {

    @Test
    @DisplayName("INV-ADV-007: превышение частоты — 429 RATE_LIMITED с Retry-After")
    void rateLimitReturns429() throws Exception {
        UUID model = lockedModel(architect(), "Частота");
        String body = "{\"modelId\":\"" + model + "\"}";
        for (int i = 0; i < 5; i++) {
            assertThat(ask("review", as("rate-limited", "ARCHITECT"), "rate-" + i, body).status()).isEqualTo(200);
        }

        Response limited = ask("review", as("rate-limited", "ARCHITECT"), "rate-5", body);

        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.body()).contains("\"code\":\"RATE_LIMITED\"");
        assertThat(Integer.parseInt(limited.retryAfter())).isPositive();
    }
}
