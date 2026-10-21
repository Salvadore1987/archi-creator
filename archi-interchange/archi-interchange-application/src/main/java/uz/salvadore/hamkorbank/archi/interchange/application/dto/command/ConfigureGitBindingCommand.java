package uz.salvadore.hamkorbank.archi.interchange.application.dto.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Привязка рабочего пространства к репозиторию — как её прислал клиент. Значения проверяет сценарий.
 *
 * @param branch   ветка; {@code null} или пустая — по умолчанию
 * @param tokenRef ссылка на секрет с токеном, а не сам токен; {@code null} — не задана
 * @param authors  соответствие субъекта Keycloak автору коммита
 */
public record ConfigureGitBindingCommand(UUID workspaceId, String repoUrl, String branch, String tokenRef,
                                         List<Author> authors) {

    public ConfigureGitBindingCommand {
        Objects.requireNonNull(workspaceId, "workspaceId");
        authors = authors == null ? List.of() : List.copyOf(authors);
    }

    /** Субъект Keycloak и автор коммитов от его имени. */
    public record Author(String subject, String name, String email) {
    }
}
