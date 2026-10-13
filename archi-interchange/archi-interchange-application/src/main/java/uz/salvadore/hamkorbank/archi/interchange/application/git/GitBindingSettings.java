package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Настройка привязки, как её задал администратор. Проверяется доменом при
 * построении привязки в сценарии, а не здесь: отказ — ответ сценария, а не адаптера.
 *
 * @param branch пусто — ветка по умолчанию
 */
public record GitBindingSettings(String repoUrl, Optional<String> branch, String tokenRef, List<Author> authors) {

    public GitBindingSettings {
        Objects.requireNonNull(branch, "branch");
        authors = List.copyOf(authors);
    }

    /** Субъект Keycloak и автор коммита от его имени. */
    public record Author(String subject, String name, String email) {
    }
}
