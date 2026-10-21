package uz.salvadore.hamkorbank.archi.bootstrap.git;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
public record GitSettings(Path cacheDir,
                          @DefaultValue("oauth2") String username,
                          @DefaultValue("Archi Creator") String committerName,
                          @DefaultValue("archi-creator@localhost") String committerEmail,
                          @DefaultValue("users.archi-creator.local") String authorEmailDomain,
                          @DefaultValue("10s") Duration pollInterval,
                          @DefaultValue("30s") Duration timeout,
                          @DefaultValue("5s") Duration retryBase,
                          @DefaultValue("10m") Duration retryMax,
                          @DefaultValue("30d") Duration outboxRetention,
                          @DefaultValue("false") boolean allowLocalRepositories) {
}
