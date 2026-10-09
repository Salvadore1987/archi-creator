package uz.salvadore.hamkorbank.archi.bootstrap.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Правила доступа для всех профилей, кроме {@code dev}.
 *
 * <p>Аутентификация — {@code Authorization: Bearer <JWT>} от Keycloak.
 * Сессии нет и быть не должно: токен приходит с каждым запросом, состояние
 * на сервере не хранится, поэтому CSRF-защита выключена — красть нечего.
 *
 * <p>Правила здесь грубые: «кто-то вошёл» против «никто не входил». Разрешение
 * на конкретную операцию даёт не этот класс, а проверка роли на границе use
 * case'а — перечень «операция → роли» живёт в application-слое контекста.
 * Дублировать его в матчерах URL значило бы завести второй источник правды,
 * который разойдётся с первым.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsSettings.class)
@Profile("!dev")
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, CorsSettings cors) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(corsCustomizer(cors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Пробы живучести нужны оркестратору до всякого токена.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Остальной actuator — метрики и prometheus — административный,
                        // и наружу без роли не выставляется.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Преполётные запросы браузера уходят без Authorization.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Адрес входа фронтенд узнаёт до того, как у него появится токен.
                        .requestMatchers(HttpMethod.GET, "/api/v1/ui-config").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Статика фронтенда: сам SPA открыт, данные за ним — нет.
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Роли Keycloak из {@code realm_access.roles} — в полномочия {@code ROLE_*}.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());
        return converter;
    }

    /**
     * Источник настроек CORS. Пустой список источников оставляет CORS закрытым:
     * конфигурация не регистрируется вовсе, а не регистрируется пустой.
     */
    private static Customizer<org.springframework.security.config.annotation.web.configurers.CorsConfigurer<HttpSecurity>> corsCustomizer(
            CorsSettings settings) {
        return settings.closed()
                ? cors -> cors.disable()
                : cors -> cors.configurationSource(corsConfigurationSource(settings));
    }

    private static CorsConfigurationSource corsConfigurationSource(CorsSettings settings) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(settings.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "If-Match"));
        configuration.setExposedHeaders(List.of("ETag", "Location", "Retry-After"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
