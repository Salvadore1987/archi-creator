package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_version")
public class ModelVersionEntity {

    @Id
    public UUID id;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "version_no", nullable = false, updatable = false)
    public long versionNo;

    @Column(nullable = false, updatable = false)
    public String author;

    @Column(updatable = false)
    public String comment;

    public String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    public byte[] snapshot;

    @Column(name = "content_hash", nullable = false, updatable = false)
    public String contentHash;

    @Column(name = "git_sha")
    public String gitSha;
}
