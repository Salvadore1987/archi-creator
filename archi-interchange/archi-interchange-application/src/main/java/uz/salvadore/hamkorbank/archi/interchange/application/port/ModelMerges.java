package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.time.Instant;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;

/**
 * Последний коммит, слитый в модель по сущностям. Его правки архитектор принял:
 * публикация не считает их расхождением, а сам коммит становится вторым родителем
 * следующего коммита, пока ветка привязки его не содержит.
 */
public interface ModelMerges {

    /** В транзакции вызывающего: вместе с версией слияния. Прежний слитый коммит заменяется. */
    void record(ModelId model, GitSha sha, Instant at);

    Optional<GitSha> last(ModelId model);
}
