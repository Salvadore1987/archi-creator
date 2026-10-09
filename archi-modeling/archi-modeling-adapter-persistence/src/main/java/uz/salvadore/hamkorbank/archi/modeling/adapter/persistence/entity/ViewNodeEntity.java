package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "view_node")
public class ViewNodeEntity {

    @Id
    public UUID id;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "view_id", nullable = false, updatable = false)
    public UUID viewId;

    @Column(name = "parent_id")
    public UUID parentId;

    @Column(name = "archi_id", nullable = false, updatable = false)
    public String archiId;

    @Column(name = "archi_type", nullable = false, updatable = false)
    public String archiType;

    @Column(nullable = false)
    public String kind;

    @Column(name = "element_id")
    public UUID elementId;

    public int x;
    public int y;
    public int width;
    public int height;

    @Column(name = "fill_color")
    public String fillColor;

    public String font;

    @Column(name = "font_color")
    public String fontColor;

    @Column(name = "line_color")
    public String lineColor;

    @Column(name = "text_alignment")
    public Integer textAlignment;

    public String label;

    public String content;

    @Column(name = "sort_order", nullable = false)
    public long sortOrder;

    @Column(name = "raw_xml")
    public String rawXml;
}
