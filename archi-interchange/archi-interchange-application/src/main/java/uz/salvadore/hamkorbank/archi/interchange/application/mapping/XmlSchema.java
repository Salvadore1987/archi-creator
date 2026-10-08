package uz.salvadore.hamkorbank.archi.interchange.application.mapping;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Что из файла Archi типизировано в столбцах (ADR-0017) и в каком порядке Archi пишет
 * атрибуты нового объекта. Одно место для обоих направлений: раскладчик и сборщик
 * обязаны знать одно и то же, иначе round-trip расходится молча.
 */
final class XmlSchema {

    private XmlSchema() {
    }

    static final String XSI_TYPE = "xsi:type";
    static final String TARGET_CONNECTIONS = "targetConnections";

    /** Атрибуты стиля, типизированные в {@code StyleOverride} (FR-21). */
    static final List<String> STYLE = List.of("fillColor", "font", "fontColor", "lineColor", "textAlignment");

    static final Set<String> ROOT_TYPED = Set.of("name", "id", "version");
    static final Set<String> FOLDER_TYPED = Set.of("name", "id", "type");
    static final Set<String> ELEMENT_TYPED = Set.of(XSI_TYPE, "name", "id");
    static final Set<String> RELATIONSHIP_TYPED = Set.of(XSI_TYPE, "name", "id", "source", "target", "accessType",
            "directed");
    static final Set<String> VIEW_TYPED = Set.of(XSI_TYPE, "name", "id", "viewpoint");
    static final Set<String> NODE_TYPED = Set.of(XSI_TYPE, "id", "archimateElement", "fillColor", "font", "fontColor",
            "lineColor", "textAlignment");
    static final Set<String> EDGE_TYPED = Set.of(XSI_TYPE, "id", "source", "target", "archimateRelationship",
            "fillColor", "font", "fontColor", "lineColor", "textAlignment");

    /** Порядок атрибутов нового объекта — как его записал бы Archi. */
    static final List<String> ROOT_ORDER = List.of("name", "id", "version");
    static final List<String> FOLDER_ORDER = List.of("name", "id", "type");
    static final List<String> ELEMENT_ORDER = List.of(XSI_TYPE, "name", "id");
    static final List<String> RELATIONSHIP_ORDER = List.of(XSI_TYPE, "name", "id", "source", "target", "accessType",
            "directed");
    static final List<String> VIEW_ORDER = List.of(XSI_TYPE, "name", "id", "viewpoint");
    static final List<String> NODE_ORDER = List.of(XSI_TYPE, "id", TARGET_CONNECTIONS, "fillColor", "font",
            "fontColor", "lineColor", "textAlignment", "archimateElement");
    static final List<String> EDGE_ORDER = List.of(XSI_TYPE, "id", TARGET_CONNECTIONS, "fillColor", "font",
            "fontColor", "lineColor", "textAlignment", "source", "target", "archimateRelationship");

    /** Атрибуты типизированных значений и их значения по умолчанию в Archi (EMF их не пишет). */
    static final List<String> BOUNDS = List.of("x", "y", "width", "height");
    static final Map<String, Integer> BOUNDS_DEFAULT = Map.of("x", 0, "y", 0, "width", -1, "height", -1);
    static final List<String> BENDPOINT = List.of("startX", "startY", "endX", "endY");
    static final List<String> PROPERTY = List.of("key", "value");

    /**
     * Позиции типизированных значений нового объекта — раньше любого содержимого
     * с позицией из файла: Archi пишет документацию, свойства и геометрию первыми.
     */
    static final long DOCUMENTATION_FIRST = -3;
    static final long PROPERTIES_FIRST = -2;
    static final long BOUNDS_FIRST = -1;
    /** Позиция «после всего» — для описания и свойств корня, которые Archi пишет за папками. */
    static final long AFTER_ALL = Long.MAX_VALUE - 1;

    /** Пространства имён корня новой модели — как у Archi. */
    static final List<String[]> NEW_MODEL_NAMESPACES = List.of(
            new String[] {"xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance"},
            new String[] {"xmlns:archimate", "http://www.archimatetool.com/archimate"});
}
