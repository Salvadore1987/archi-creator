package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.Layer;

/**
 * Объект ArchiMate. Существует в модели один раз и размещается на любом числе
 * представлений (FR-11).
 *
 * @param supported тип редактируется в текущей фазе; {@code false} — opaque (FR-03):
 *                  хранится и выгружается, но не правится
 */
public record Element(ElementId id, FolderId folderId, ArchiId archiId, ArchiType archiType, String name,
                      Optional<String> documentation, List<PropertyEntry> properties, SortOrder sortOrder,
                      boolean supported, Optional<RawXml> rawXml) {

    public Element {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(folderId, "folderId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(documentation, "documentation");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(rawXml, "rawXml");
        Names.limited(name, "элемента");
        properties = List.copyOf(properties);
        if (!supported && rawXml.isEmpty()) {
            throw new IllegalArgumentException("FR-03: у opaque-элемента " + archiId + " нет raw_xml");
        }
    }

    public Layer layer() {
        return ArchiTypeRegistry.archimate32().layerOf(archiType);
    }

    public Element edited(String newName, Optional<String> newDocumentation, List<PropertyEntry> newProperties) {
        return new Element(id, folderId, archiId, archiType, newName, newDocumentation, newProperties, sortOrder,
                supported, rawXml);
    }

    public Element movedTo(FolderId folder, SortOrder order) {
        return new Element(id, folder, archiId, archiType, name, documentation, properties, order, supported, rawXml);
    }
}
