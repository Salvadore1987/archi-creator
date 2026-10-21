package uz.salvadore.hamkorbank.archi.bootstrap.security;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Правила доступа для всех профилей, кроме {@code dev} без {@code prod}.
 *
 * <p>Аутентификация — {@code Authorization: Bearer <JWT>} от Keycloak.
 * Роли Keycloak лежат в {@code realm_access.roles} — вложенным объектом, а не плоским
 * списком; в полномочия {@code ROLE_*} их переводит Spring Boot по выражению
 * {@code spring.security.oauth2.resourceserver.jwt.authorities-claim-expressions}.
 * Сессии нет и быть не должно: токен приходит с каждым запросом, состояние
 * на сервере не хранится, поэтому CSRF-защита выключена — красть нечего.
 *
 * <p>Цепочек три — Actuator, API и всё прочее (статика SPA): у каждой свой набор открытых адресов,
 * и порядок матчеров внутри одной цепочки доступа не решает. Открытое перечислено явно,
 * остальное закрыто.
 *
 * <p>Правила здесь грубые: «кто-то вошёл» против «никто не входил». Разрешение
 * на конкретную операцию даёт не этот класс, а проверка роли на границе use
 * case'а — перечень «операция → роли» живёт в application-слое контекста.
 * Дублировать его в матчерах URL значило бы завести второй источник правды,
 * который разойдётся с первым.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(CorsSettings.class)
@Profile("!dev | prod")
public class SecurityConfig {

    private static final String API = "/api/**";
    /** Статика SPA: сам интерфейс открыт, данные за ним — нет. */
    private static final String[] SPA = {"/", "/index.html", "/assets/**"};

    /**
     * Actuator: пробы живучести и сведения о сборке нужны оркестратору до всякого токена,
     * метрики и prometheus — административные. Адреса — по идентификаторам эндпоинтов, а не по пути:
     * смена базового пути Actuator правило не обходит.
     */
    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** API: всё — по токену, кроме адреса входа, который фронтенд узнаёт до токена. */
    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, CorsSettings cors) throws Exception {
        http
                .securityMatcher(API)
                .csrf(csrf -> csrf.disable())
                .cors(corsCustomizer(cors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Преполётные запросы браузера уходят без Authorization.
                        .requestMatchers(HttpMethod.OPTIONS, API).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ui-config").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Всё прочее: открыта только статика SPA и страница ошибки, на которую контейнер
     * переадресует отказ. Адрес вне списка — отказ, а не открытый обработчик: новый контроллер
     * мимо {@code /api} без токена наружу не выйдет.
     */
    @Bean
    @Order(3)
    SecurityFilterChain uiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, SPA).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    /**
     * Источник настроек CORS. Пустой список источников оставляет CORS закрытым:
     * конфигурация не регистрируется вовсе, а не регистрируется пустой.
     */
    private static Customizer<CorsConfigurer<HttpSecurity>> corsCustomizer(CorsSettings settings) {
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
