package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Привязка пространства к репозиторию: адрес, ветка, ссылка на секрет и состояние.
 *
 * @param status        {@code CONFIGURED}, {@code UNREACHABLE} или {@code DISABLED}
 * @param lastCommitSha последний коммит ветки, известный серверу; {@code null} — коммитов ещё не было
 * @param lastCheckedAt когда репозиторий проверялся; {@code null} — не проверялся
 */
public record GitBindingResponse(String repoUrl, String branch, String tokenRef, String status,
                                 @Nullable String lastCommitSha, @Nullable Instant lastCheckedAt,
                                 List<Author> authors) {

    public GitBindingResponse {
        authors = List.copyOf(authors);
    }

    /** Субъект Keycloak и автор коммитов от его имени. */
    public record Author(String subject, String name, String email) {
    }
}
