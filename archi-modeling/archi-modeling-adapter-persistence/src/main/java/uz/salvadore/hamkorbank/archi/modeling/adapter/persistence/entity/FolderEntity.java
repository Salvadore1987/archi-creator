package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Строка папки дерева модели: родитель, тип корневой папки, порядок среди соседей и остаток XML. */
@Entity
@Table(name = "model_folder")
public class FolderEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "parent_id")
    public @Nullable UUID parentId;

    @Column(name = "archi_id", nullable = false, updatable = false)
    public String archiId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "folder_type")
    public @Nullable String folderType;

    @Column(name = "sort_order", nullable = false)
    public long sortOrder;

    @Column(name = "raw_xml")
    public @Nullable String rawXml;
}
