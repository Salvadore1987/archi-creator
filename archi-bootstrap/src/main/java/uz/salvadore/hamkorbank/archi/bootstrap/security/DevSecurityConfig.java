package uz.salvadore.hamkorbank.archi.bootstrap.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Правила доступа профиля {@code dev}: Keycloak не поднимается,
 * авторизация подменяется заглушкой {@code ARCHITECT}.
 *
 * <p>Проверка токена здесь не выключена «на время», а отсутствует по решению:
 * поднимать Keycloak ради локальной отладки кодека — цена, которую проект
 * платить отказывается. Обратная сторона названа прямо: правила доступа
 * в dev не проверяются, и ошибка в них видна только на профиле {@code prod}
 * или в интеграционном тесте.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsSettings.class)
@Profile("dev")
public class DevSecurityConfig {

    @Bean
    SecurityFilterChain devSecurityFilterChain(HttpSecurity http, CorsSettings cors) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(devCorsSource(cors)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new DevArchitectAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * В dev Vite ходит на {@code :8080} через прокси, но прямые запросы
     * из браузера при отладке тоже должны работать.
     */
    private static CorsConfigurationSource devCorsSource(CorsSettings settings) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(settings.allowedOrigins());
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
