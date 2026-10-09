package uz.salvadore.hamkorbank.archi.bootstrap.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Что фронтенду нужно знать до входа ({@code archi.ui.*}).
 *
 * @param oidc куда вести браузер за токеном
 */
@ConfigurationProperties("archi.ui")
public record UiSettings(@DefaultValue Oidc oidc) {

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
                       @DefaultValue("archi-creator-ui") String clientId) {
    }
}
