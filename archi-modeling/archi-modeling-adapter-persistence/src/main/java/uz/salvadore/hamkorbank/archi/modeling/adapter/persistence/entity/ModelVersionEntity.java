package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.jspecify.annotations.Nullable;

/**
 * Строка версии модели: номер, автор, комментарий, метка, сжатый снимок с отпечатком и коммит Git.
 * Метку, коммит и снимок меняют разные сценарии, поэтому запись обновляет только изменённые столбцы:
 * иначе сценарий, прочитавший строку раньше, вернул бы чужому столбцу прочитанное значение.
 */
@DynamicUpdate
@Entity
@Table(name = "model_version")
public class ModelVersionEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "version_no", nullable = false, updatable = false)
    public long versionNo;

    @Column(name = "author", nullable = false, updatable = false)
    public String author;

    @Column(name = "comment", updatable = false)
    public @Nullable String comment;

    @Column(name = "label")
    public @Nullable String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "snapshot")
    public byte @Nullable [] snapshot;

    @Column(name = "content_hash", nullable = false, updatable = false)
    public String contentHash;

    @Column(name = "git_sha")
    public @Nullable String gitSha;
}
