package uz.salvadore.hamkorbank.archi.bootstrap.git;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки выгрузки в Git ({@code archi.git.*}).
 *
 * @param cacheDir          bare-клоны рабочих пространств; кэш — потеря стоит одного fetch
 * @param username          имя для токена по HTTPS: GitLab и GitHub принимают любое непустое
 * @param committerName     коммитер — сервер; автор — архитектор, сохранивший версию
 * @param committerEmail    адрес коммитера
 * @param authorEmailDomain домен адреса автора, которого нет в привязке: {@code субъект@домен}
 * @param pollInterval      пауза между проходами очереди публикации
 * @param timeout           предел одного сетевого обращения к репозиторию
 * @param retryBase         первая пауза после отказа сети; дальше — вдвое
 * @param retryMax          предел паузы
 * @param outboxRetention   сколько хранятся закрытые записи очереди публикации
 * @param allowLocalRepositories можно ли привязать репозиторий в файловой системе сервера ({@code file://}) —
 *                          только для тестов
 */
@ConfigurationProperties("archi.git")
@Validated
public record GitSettings(@NotNull Path cacheDir,
                          @DefaultValue("oauth2") @NotBlank String username,
                          @DefaultValue("Archi Creator") @NotBlank String committerName,
                          @DefaultValue("archi-creator@localhost") @NotBlank String committerEmail,
                          @DefaultValue("users.archi-creator.local") @NotBlank String authorEmailDomain,
                          @DefaultValue("10s") @NotNull Duration pollInterval,
                          @DefaultValue("30s") @NotNull Duration timeout,
                          @DefaultValue("5s") @NotNull Duration retryBase,
                          @DefaultValue("10m") @NotNull Duration retryMax,
                          @DefaultValue("30d") @NotNull Duration outboxRetention,
                          @DefaultValue("false") boolean allowLocalRepositories) {
}
