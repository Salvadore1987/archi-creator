package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "relationship")
public class RelationshipEntity {

    @Id
    public UUID id;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "folder_id", nullable = false)
    public UUID folderId;

    @Column(name = "archi_id", nullable = false, updatable = false)
    public String archiId;

    @Column(name = "archi_type", nullable = false)
    public String archiType;

    @Column(name = "source_element_id")
    public UUID sourceElementId;

    @Column(name = "source_relationship_id")
    public UUID sourceRelationshipId;

    @Column(name = "target_element_id")
    public UUID targetElementId;

    @Column(name = "target_relationship_id")
    public UUID targetRelationshipId;

    public String name;

    public String documentation;

    @Column(name = "access_type")
    public String accessType;

    public Boolean directed;

    @Column(name = "sort_order", nullable = false)
    public long sortOrder;

    @Column(nullable = false)
    public boolean supported;

    @Column(name = "raw_xml")
    public String rawXml;

    @ElementCollection
    @CollectionTable(name = "relationship_property", joinColumns = @JoinColumn(name = "owner_id"))
    @OrderBy("sortOrder")
    public List<PropertyEmbeddable> properties = new ArrayList<>();
}
