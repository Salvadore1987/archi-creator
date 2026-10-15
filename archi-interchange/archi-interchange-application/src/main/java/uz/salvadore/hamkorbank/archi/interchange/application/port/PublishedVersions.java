package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Версии modeling глазами публикации: снимок, автор, комментарий и связь с коммитом. */
public interface PublishedVersions {

    /** Модели или версии нет, снимок очищен — пусто. */
    Optional<Version> version(ModelId model, long versionNo);

    /** В транзакции вызывающего: вместе с закрытием записи outbox. */
    void recordGitSha(ModelId model, long versionNo, GitSha sha);

    /** Последняя версия модели, попавшая в Git, и её коммит. */
    Optional<Published> lastPublished(ModelId model);

    /** Пространство модели, если актор её видит; иначе — «не найдено». */
    WorkspaceId visibleWorkspace(EditorIdentity actor, ModelId model);

    /** @param author субъект Keycloak, сохранивший версию */
    record Version(WorkspaceId workspace, String modelName, long versionNo, String author, Optional<String> comment,
                   Instant createdAt, String contentHash, byte[] xml) {

        public Version {
            xml = Arrays.copyOf(xml, xml.length);
        }

        @Override
        public byte[] xml() {
            return Arrays.copyOf(xml, xml.length);
        }
    }

    /** Опубликованная версия модели и коммит, в котором она лежит. */
    record Published(long versionNo, GitSha sha) {
    }
}
