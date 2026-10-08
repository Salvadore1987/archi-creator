package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Names;

/** Собственные поля представления без узлов и рёбер — строка {@code view}. */
public record ViewHeader(ViewId id, ModelId modelId, FolderId folderId, ArchiId archiId, DiagramType archiType,
                         String name, Optional<String> documentation, Optional<String> viewpoint,
                         List<PropertyEntry> properties, SortOrder sortOrder, Optional<RawXml> rawXml,
                         long version) {

    public ViewHeader {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(folderId, "folderId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(documentation, "documentation");
        Objects.requireNonNull(viewpoint, "viewpoint");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(rawXml, "rawXml");
        Names.limited(name, "представления");
        properties = List.copyOf(properties);
    }

    /** Редактируется только диаграмма ArchiMate; скетч и холст хранятся как есть (FR-03). */
    public boolean editable() {
        return archiType.equals(DiagramType.DIAGRAM_MODEL);
    }
}
