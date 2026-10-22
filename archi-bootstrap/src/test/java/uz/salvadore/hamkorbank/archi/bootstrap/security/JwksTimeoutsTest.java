package uz.salvadore.hamkorbank.archi.bootstrap.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder;

class JwksTimeoutsTest {

    @Test
    @DisplayName("зависший Keycloak отвечает отказом в пределах настройки, а не через полминуты")
    void hangingKeycloakTimesOut() throws IOException {
        // Соединение ложится в очередь сокета, но никто его не принимает и не отвечает: так выглядит зависший Keycloak.
        try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            JwkSetUriJwtDecoderBuilder builder = NimbusJwtDecoder
                    .withJwkSetUri("http://127.0.0.1:" + silent.getLocalPort() + "/certs");
            new JwksTimeouts(new JwksSettings(Duration.ofMillis(300))).customize(builder);
            JwtDecoder decoder = builder.build();
            long started = System.nanoTime();
            assertThatThrownBy(() -> decoder.decode(token())).isInstanceOf(JwtException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        }
    }

    /** Подписанный по форме токен с незнакомым ключом: декодер обязан пойти за ключами. */
    private static String token() {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        return url.encodeToString("{\"alg\":\"RS256\",\"kid\":\"k1\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + url.encodeToString("{\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + url.encodeToString(new byte[256]);
    }
}
