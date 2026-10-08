package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "model_folder")
public class FolderEntity {

    @Id
    public UUID id;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "parent_id")
    public UUID parentId;

    @Column(name = "archi_id", nullable = false, updatable = false)
    public String archiId;

    @Column(nullable = false)
    public String name;

    @Column(name = "folder_type")
    public String folderType;

    @Column(name = "sort_order", nullable = false)
    public long sortOrder;

    @Column(name = "raw_xml")
    public String rawXml;
}
