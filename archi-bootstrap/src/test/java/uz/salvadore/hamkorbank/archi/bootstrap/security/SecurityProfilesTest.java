package uz.salvadore.hamkorbank.archi.bootstrap.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Profiles;
import org.springframework.mock.env.MockEnvironment;

/**
 * Какая цепочка безопасности включается при наборе профилей: заглушка {@code ARCHITECT} — только
 * в чистом {@code dev}; профиль {@code prod} рядом с {@code dev} её не включает.
 */
class SecurityProfilesTest {

    static Stream<Arguments> profiles() {
        return Stream.of(
                Arguments.of(List.of(), SecurityConfig.class),
                Arguments.of(List.of("prod"), SecurityConfig.class),
                Arguments.of(List.of("dev"), DevSecurityConfig.class),
                Arguments.of(List.of("dev", "prod"), SecurityConfig.class),
                Arguments.of(List.of("prod", "dev"), SecurityConfig.class));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("profiles")
    @DisplayName("ровно одна цепочка на набор профилей, заглушка — только в dev без prod")
    void exactlyOneChain(List<String> active, Class<?> expected) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(active.toArray(String[]::new));

        List<Class<?>> enabled = Stream.of(SecurityConfig.class, DevSecurityConfig.class)
                .filter(config -> environment.acceptsProfiles(
                        Profiles.of(config.getAnnotation(Profile.class).value())))
                .<Class<?>>map(config -> config)
                .toList();

        assertEquals(List.of(expected), enabled);
    }
}
