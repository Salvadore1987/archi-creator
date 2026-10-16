package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.Layer;

/**
 * Объект ArchiMate. Существует в модели один раз и размещается на любом числе
 * представлений.
 *
 * @param documentation документация; {@code null} — не задана
 * @param rawXml        остаток XML; {@code null} — нет
 * @param supported тип редактируется в текущей фазе; {@code false} — opaque:
 *                  хранится и выгружается, но не правится
 */
public record Element(ElementId id, FolderId folderId, ArchiId archiId, ArchiType archiType, String name,
                      String documentation, List<PropertyEntry> properties, SortOrder sortOrder,
                      boolean supported, RawXml rawXml) {

    public Element {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(folderId, "folderId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Names.limited(name, Names.ELEMENT);
        properties = List.copyOf(properties);
        if (!supported && rawXml == null) {
            throw new InvalidValueException(ModelingMessages.OPAQUE_WITHOUT_RESIDUE, archiId);
        }
    }

    /** Слой ArchiMate по типу элемента; у незнакомого типа — прочее. */
    public Layer layer() {
        return ArchiTypeRegistry.archimate32().layerOf(archiType);
    }

    /**
     * Тот же элемент с новыми именем, документацией и свойствами; место в дереве не меняется.
     * {@code null} в документации — документации нет.
     */
    public Element edited(String newName, String newDocumentation, List<PropertyEntry> newProperties) {
        return new Element(id, folderId, archiId, archiType, newName, newDocumentation, newProperties, sortOrder,
                supported, rawXml);
    }

    /** Тот же элемент в другой папке и на другой позиции. */
    public Element movedTo(FolderId folder, SortOrder order) {
        return new Element(id, folder, archiId, archiType, name, documentation, properties, order, supported, rawXml);
    }
}
