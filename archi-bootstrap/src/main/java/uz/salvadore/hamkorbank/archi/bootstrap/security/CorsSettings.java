package uz.salvadore.hamkorbank.archi.bootstrap.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Разрешённые источники кросс-доменных запросов ({@code archi.web.cors}).
 *
 * <p>Пустой список — закрытый CORS, и это состояние по умолчанию (§10.3).
 * В prod фронтенд отдаётся тем же приложением, поэтому кросс-доменных запросов
 * быть не должно; список заполняется только в dev, где рядом живёт Vite.
 *
 * @param allowedOrigins адреса вида {@code http://localhost:5173}; маска
 *                       {@code *} не поддерживается намеренно
 */
@ConfigurationProperties(prefix = "archi.web.cors")
public record CorsSettings(List<String> allowedOrigins) {

    public CorsSettings {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    boolean closed() {
        return allowedOrigins.isEmpty();
    }
}
