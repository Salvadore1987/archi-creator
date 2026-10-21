package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Строка привязки рабочего пространства к Git-репозиторию: адрес, ветка, ссылка на секрет и авторы.
 * Голову ветки пишет публикация, остальное — администратор и проверка доступности, поэтому запись
 * обновляет только изменённые столбцы и не откатывает чужой.
 */
@DynamicUpdate
@Entity
@Table(name = "git_binding")
public class GitBindingEntity {

    @Id
    @Column(name = "workspace_id")
    public UUID workspaceId;

    @Column(name = "repo_url", nullable = false)
    public String repoUrl;

    @Column(name = "branch", nullable = false)
    public String branch;

    @Column(name = "token_ref", nullable = false)
    public String tokenRef;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "last_commit_sha")
    public String lastCommitSha;

    @Column(name = "last_checked_at")
    public Instant lastCheckedAt;

    @ElementCollection
    @CollectionTable(name = "git_author", joinColumns = @JoinColumn(name = "workspace_id"))
    @OrderColumn(name = "sort_order")
    public List<Author> authors = new ArrayList<>();

    /** Автор коммитов от имени пользователя Keycloak; порядок в списке хранится столбцом {@code sort_order}. */
    @Embeddable
    public static class Author {

        @Column(name = "subject", nullable = false)
        public String subject;

        @Column(name = "name", nullable = false)
        public String name;

        @Column(name = "email", nullable = false)
        public String email;
    }
}
