package uz.salvadore.hamkorbank.archi.bootstrap.security;

import org.springframework.boot.security.oauth2.server.resource.autoconfigure.JwkSetUriJwtDecoderBuilderCustomizer;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder;
import org.springframework.web.client.RestTemplate;

/**
 * Пределы соединения и чтения для обращений декодера JWT к Keycloak — и за метаданными issuer'а, и за ключами.
 * Клиент — {@code RestTemplate}: другой декодер Spring Security не принимает.
 */
public final class JwksTimeouts implements JwkSetUriJwtDecoderBuilderCustomizer {

    private final JwksSettings settings;

    public JwksTimeouts(JwksSettings settings) {
        this.settings = settings;
    }

    @Override
    public void customize(JwkSetUriJwtDecoderBuilder builder) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(settings.jwksTimeout());
        requests.setReadTimeout(settings.jwksTimeout());
        builder.restOperations(new RestTemplate(requests));
    }
}
