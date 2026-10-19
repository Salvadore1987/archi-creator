package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.util.List;

/**
 * Настройка привязки, как её задал администратор. Проверяется доменом при
 * построении привязки в сценарии, а не здесь: отказ — ответ сценария, а не адаптера.
 *
 * @param branch {@code null} или пустая — ветка по умолчанию
 */
public record GitBindingSettings(String repoUrl, String branch, String tokenRef, List<Author> authors) {

    public GitBindingSettings {
        authors = List.copyOf(authors);
    }

    /** Субъект Keycloak и автор коммита от его имени. */
    public record Author(String subject, String name, String email) {
    }
}
