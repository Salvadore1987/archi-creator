package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Узел представления: вид, элемент, родительский узел, границы относительно родителя, стиль и текст. */
@Entity
@Table(name = "view_node")
public class ViewNodeEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "view_id", nullable = false, updatable = false)
    public UUID viewId;

    @Column(name = "parent_id")
    public @Nullable UUID parentId;

    @Column(name = "archi_id", nullable = false, updatable = false)
    public String archiId;

    @Column(name = "archi_type", nullable = false, updatable = false)
    public String archiType;

    @Column(name = "kind", nullable = false)
    public String kind;

    @Column(name = "element_id")
    public @Nullable UUID elementId;

    @Column(name = "x")
    public int x;
    @Column(name = "y")
    public int y;
    @Column(name = "width")
    public int width;
    @Column(name = "height")
    public int height;

    @Column(name = "fill_color")
    public @Nullable String fillColor;

    @Column(name = "font")
    public @Nullable String font;

    @Column(name = "font_color")
    public @Nullable String fontColor;

    @Column(name = "line_color")
    public @Nullable String lineColor;

    @Column(name = "text_alignment")
    public @Nullable Integer textAlignment;

    @Column(name = "label")
    public @Nullable String label;

    @Column(name = "content")
    public @Nullable String content;

    @Column(name = "sort_order", nullable = false)
    public long sortOrder;

    @Column(name = "raw_xml")
    public @Nullable String rawXml;
}
