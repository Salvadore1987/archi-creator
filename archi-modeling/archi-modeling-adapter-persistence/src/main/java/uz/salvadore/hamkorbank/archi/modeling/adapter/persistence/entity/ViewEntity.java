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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Строка представления: место в дереве папок, точка зрения и остаток XML. */
@Entity
@Table(name = "view")
public class ViewEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "folder_id", nullable = false)
    public UUID folderId;

    @Column(name = "archi_id", nullable = false, updatable = false)
    public String archiId;

    @Column(name = "archi_type", nullable = false, updatable = false)
    public String archiType;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "documentation")
    public @Nullable String documentation;

    @Column(name = "viewpoint")
    public @Nullable String viewpoint;

    @Column(name = "sort_order", nullable = false)
    public long sortOrder;

    @Column(name = "raw_xml")
    public @Nullable String rawXml;

    @Version
    @Column(name = "version")
    public long version;

    @ElementCollection
    @CollectionTable(name = "view_property", joinColumns = @JoinColumn(name = "owner_id"))
    @OrderBy("sortOrder")
    public List<PropertyEmbeddable> properties = new ArrayList<>();
}
