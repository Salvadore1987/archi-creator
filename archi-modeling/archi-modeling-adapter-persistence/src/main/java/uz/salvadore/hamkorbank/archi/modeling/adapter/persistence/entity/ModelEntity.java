package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Строка заголовка модели: имя, статус жизненного цикла, автор, версия строки и остаток XML корня. */
@Entity
@Table(name = "model")
public class ModelEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    public UUID workspaceId;

    @Column(name = "archi_id", nullable = false, updatable = false)
    public String archiId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "documentation")
    public String documentation;

    @Column(name = "archi_version", nullable = false)
    public String archiVersion;

    @Column(name = "status", nullable = false)
    public String status;

    @Column(name = "raw_xml")
    public String rawXml;

    @Column(name = "created_by", nullable = false, updatable = false)
    public String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /** Оптимистичная блокировка строки. */
    @Version
    @Column(name = "version")
    public long version;

    @ElementCollection
    @CollectionTable(name = "model_property", joinColumns = @JoinColumn(name = "owner_id"))
    @OrderBy("sortOrder")
    public List<PropertyEmbeddable> properties = new ArrayList<>();
}
