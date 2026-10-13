package uz.salvadore.hamkorbank.archi.bootstrap.git;

import java.util.Optional;
import org.springframework.core.env.Environment;
import uz.salvadore.hamkorbank.archi.interchange.application.port.SecretResolver;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;

/**
 * Секрет по ссылке {@code env:ИМЯ} — из окружения процесса (через {@link Environment}:
 * переменная окружения, а в тестах — свойство). Значение не кэшируется и не логируется.
 */
public final class EnvironmentSecretResolver implements SecretResolver {

    private final Environment environment;

    public EnvironmentSecretResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> resolve(SecretRef ref) {
        return Optional.ofNullable(environment.getProperty(ref.variable())).filter(v -> !v.isBlank());
    }
}
