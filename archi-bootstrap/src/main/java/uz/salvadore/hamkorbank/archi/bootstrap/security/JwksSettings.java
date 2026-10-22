package uz.salvadore.hamkorbank.archi.bootstrap.security;

import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Чтение метаданных issuer'а и ключей Keycloak ({@code archi.security}).
 *
 * @param jwksTimeout предел соединения и предел чтения ответа Keycloak; чтение идёт под блокировкой декодера,
 *                    поэтому без предела зависший Keycloak держал бы все запросы с токеном. Ноль у фабрики
 *                    соединений — бесконечность, поэтому не меньше миллисекунды
 */
@ConfigurationProperties(prefix = "archi.security")
@Validated
public record JwksSettings(@DefaultValue("3s") @DurationMin(millis = 1) Duration jwksTimeout) {
}
