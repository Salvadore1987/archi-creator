package uz.salvadore.hamkorbank.archi.modeling.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "view_edge")
public class ViewEdgeEntity {

    @Id
    public UUID id;

    @Column(name = "model_id", nullable = false, updatable = false)
    public UUID modelId;

    @Column(name = "view_id", nullable = false, updatable = false)
    public UUID viewId;

    @Column(name = "archi_id", nullable = false, updatable = false)
    public String archiId;

    @Column(name = "archi_type", nullable = false, updatable = false)
    public String archiType;

    @Column(name = "relationship_id")
    public UUID relationshipId;

    @Column(name = "source_node_id")
    public UUID sourceNodeId;

    @Column(name = "source_edge_id")
    public UUID sourceEdgeId;

    @Column(name = "target_node_id")
    public UUID targetNodeId;

    @Column(name = "target_edge_id")
    public UUID targetEdgeId;

    /** {@code [[startX,startY,endX,endY], …]}; разбирает {@code Bendpoints}. */
    @Column(nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    public String bendpoints;

    @Column(name = "fill_color")
    public String fillColor;

    public String font;

    @Column(name = "font_color")
    public String fontColor;

    @Column(name = "line_color")
    public String lineColor;

    @Column(name = "text_alignment")
    public Integer textAlignment;

    @Column(name = "sort_order", nullable = false)
    public long sortOrder;

    @Column(name = "raw_xml")
    public String rawXml;
}
