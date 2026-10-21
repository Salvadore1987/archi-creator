package uz.salvadore.hamkorbank.archi.bootstrap.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Что фронтенду нужно знать до входа ({@code archi.ui.*}).
 *
 * @param oidc куда вести браузер за токеном
 */
@ConfigurationProperties("archi.ui")
@Validated
public record UiSettings(@DefaultValue @Valid Oidc oidc) {

    /**
     * Вход через Keycloak глазами браузера.
     *
     * @param enabled   выключен — входа нет вовсе: в {@code dev} каждый запрос
     *                  приходит от заглушки {@code ARCHITECT}
     * @param authority адрес realm'а, видимый из браузера; внутри сети приложение может
     *                  ходить к Keycloak по другому адресу
     * @param clientId  публичный клиент SPA в realm'е
     */
    public record Oidc(@DefaultValue("true") boolean enabled, @DefaultValue("") String authority,
                       @DefaultValue("archi-creator-ui") @NotBlank String clientId) {

        /** Включённый вход без адреса realm'а увёл бы браузер в никуда. */
        @AssertTrue
        public boolean isAuthorityGiven() {
            return !enabled || authority != null && !authority.isBlank();
        }
    }
}
