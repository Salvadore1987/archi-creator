package uz.salvadore.hamkorbank.archi.interchange.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_session")
public class ImportSessionEntity {

    @Id
    public UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    public UUID workspaceId;

    @Column(name = "target_model_id")
    public UUID targetModelId;

    @Column(name = "source_name", nullable = false)
    public String sourceName;

    @Column(name = "source_hash", nullable = false)
    public String sourceHash;

    @Column(name = "source_size", nullable = false)
    public long sourceSize;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    public String idempotencyKey;

    @Column(name = "strict_mode", nullable = false)
    public boolean strictMode;

    @Column(nullable = false)
    public String status;

    @Column(name = "started_by", nullable = false)
    public String startedBy;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt;

    @Column(name = "finished_at")
    public Instant finishedAt;

    @Column(name = "model_id")
    public UUID modelId;

    @Column(name = "version_no")
    public Long versionNo;
}
