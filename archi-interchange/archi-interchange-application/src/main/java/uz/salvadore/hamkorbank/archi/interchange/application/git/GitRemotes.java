package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Remote;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Unreachable;
import uz.salvadore.hamkorbank.archi.interchange.application.port.SecretResolver;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;

/**
 * Общее у публикации и импорта: учётные данные привязки на время вызова и каталог
 * модели в коммите — {@code models/<слаг>.<8 hex от ModelId>}, найденный по суффиксу.
 */
final class GitRemotes {

    static final String MODELS_DIR = "models";

    private final SecretResolver secrets;
    private final TextCatalog texts;

    GitRemotes(SecretResolver secrets, TextCatalog texts) {
        this.secrets = secrets;
        this.texts = texts;
    }

    /** Секрета нет — репозиторий недоступен; локальному {@code file:} токен не нужен. */
    Remote remote(GitBinding binding) {
        Optional<String> token = secrets.resolve(binding.tokenRef());
        if (token.isEmpty() && !binding.repoUrl().startsWith("file:")) {
            throw new Unreachable(texts.text(Message.of(InterchangeMessages.GIT_SECRET_MISSING,
                    binding.tokenRef().value())), null);
        }
        return new Remote(binding.workspaceId(), binding.repoUrl(), binding.branch(), token);
    }

    /** Путь каталога модели от корня; в коммите его нет — пусто. */
    static Optional<String> modelDirectory(GitRepositoryPort repository, Remote remote, GitSha commit,
                                           ModelId model) {
        String suffix = "." + shortId(model);
        return repository.directories(remote, commit, MODELS_DIR).stream()
                .filter(name -> name.endsWith(suffix)).sorted().findFirst().map(name -> MODELS_DIR + "/" + name);
    }

    static String shortId(ModelId model) {
        return ContentHash.of(model.value().toString().getBytes(StandardCharsets.UTF_8)).value().substring(0, 8);
    }
}
