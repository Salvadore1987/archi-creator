package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Size;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Bendpoint;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Rect;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelRowRules;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.Layer;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;

/**
 * Представление из документа в терминах отрисовки: узлы с абсолютными
 * прямоугольниками и глубиной вложенности, рёбра с концами и точками перегиба.
 * Подписи и признак «не поддержан» — как у канвы: имя берётся из элемента модели,
 * у группы — из узла, у заметки — её текст.
 */
final class ViewScene {

    private static final ArchiTypeRegistry REGISTRY = ArchiTypeRegistry.archimate32();

    enum Kind {
        ELEMENT,
        GROUP,
        NOTE,
        OTHER
    }

    /**
     * Собственный стиль объекта из файла; поле {@code null} — токен слоя. Цвет берётся только
     * по правилу формата modeling: чужое значение из остатка XML в разметку не попадает.
     *
     * @param rejected имена атрибутов цвета, значения которых правило не приняло
     */
    record Style(@Nullable String fillColor, @Nullable String font, @Nullable String fontColor,
                 @Nullable String lineColor, @Nullable Integer textAlignment, List<String> rejected) {

        Style {
            rejected = List.copyOf(rejected);
        }

        static Style of(DocumentNode node) {
            List<String> rejected = new ArrayList<>();
            String fillColor = color(node, "fillColor", rejected);
            String fontColor = color(node, "fontColor", rejected);
            String lineColor = color(node, "lineColor", rejected);
            return new Style(fillColor, nonBlank(node, "font").orElse(null), fontColor, lineColor,
                    nonBlank(node, "textAlignment").flatMap(ViewScene::integer).orElse(null), rejected);
        }

        private static @Nullable String color(DocumentNode node, String name, List<String> rejected) {
            String value = nonBlank(node, name).orElse(null);
            if (value == null || ModelRowRules.color(value)) {
                return value;
            }
            rejected.add(name);
            return null;
        }
    }

    /**
     * @param shapeType тип для силуэта: тип элемента, у группы и заметки — тип узла
     * @param layer     слой элемента — для силуэтов без своего слоя
     * @param position  место среди соседей в файле — порядок отрисовки на уровне
     */
    record Node(String archiId, Kind kind, String shapeType, Layer layer, String label, boolean opaque, Rect rect,
                int depth, int position, int index, Style style, boolean image) {
    }

    /**
     * @param accessType вид доступа связи; {@code null} — атрибута нет
     * @param label      имя связи; {@code null} — пустое или нет
     */
    record Edge(String archiId, String archiType, @Nullable AccessType accessType, boolean directed,
                @Nullable String label, String sourceId, String targetId, List<Bendpoint> bendpoints, Style style,
                int position, int index) {
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Edge> edges = new LinkedHashMap<>();
    private final Map<String, DocumentNode> concepts = new HashMap<>();
    private final Notation notation;
    private int index;

    ViewScene(ModelDocument document, DocumentNode view, Notation notation) {
        this.notation = notation;
        for (DocumentNode node : document.allNodes()) {
            if (node.tag().equals("element")) {
                concepts.put(node.archiId().value(), node);
            }
        }
        List<DocumentContent> content = view.content();
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i) instanceof DocumentNode child && child.tag().equals("child")) {
                node(child, 0, 0, 0, i);
            }
        }
    }

    List<Node> nodes() {
        return List.copyOf(nodes.values());
    }

    List<Edge> edges() {
        return List.copyOf(edges.values());
    }

    Optional<Node> node(String archiId) {
        return Optional.ofNullable(nodes.get(archiId));
    }

    private void node(DocumentNode child, double parentX, double parentY, int depth, int position) {
        String xsiType = child.archiType().orElse("");
        Optional<DocumentNode> element = child.attribute("archimateElement").map(concepts::get)
                .filter(ViewScene::isElement);
        Kind kind = switch (child.viewObject()) {
            case ELEMENT -> Kind.ELEMENT;
            case GROUP -> Kind.GROUP;
            case NOTE -> Kind.NOTE;
            default -> Kind.OTHER;
        };
        String shapeType = element.flatMap(DocumentNode::archiType).orElse(xsiType);
        Layer layer = REGISTRY.findByXsiType(shapeType).map(c -> c.layer()).orElse(Layer.OTHER);
        boolean opaque = kind == Kind.OTHER || (kind == Kind.ELEMENT && element.flatMap(DocumentNode::archiType)
                .flatMap(REGISTRY::findByXsiType)
                .filter(c -> c.kind() == ConceptKind.ELEMENT || c.kind() == ConceptKind.JUNCTION)
                .filter(c -> c.supported()).isEmpty());
        Optional<String> text = child.values("content").stream().findFirst().map(DocumentValue::text);
        String label = kind == Kind.NOTE ? text.orElse("")
                : element.map(e -> e.attribute("name").orElse(""))
                        .orElseGet(() -> child.attribute("name").or(() -> text).orElse(""));

        double[] bounds = bounds(child);
        Size size = notation.effectiveSize(shapeType, bounds[2], bounds[3]);
        Rect rect = new Rect(parentX + bounds[0], parentY + bounds[1], size.width(), size.height());
        String id = child.archiId().value();
        nodes.put(id, new Node(id, kind, shapeType, layer, label, opaque, rect, depth, position, index++,
                Style.of(child), child.attribute("imagePath").isPresent()));

        List<DocumentContent> content = child.content();
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i) instanceof DocumentNode inner) {
                if (inner.tag().equals("child")) {
                    node(inner, rect.x(), rect.y(), depth + 1, i);
                } else if (inner.tag().equals("sourceConnection")) {
                    edge(inner, i);
                }
            }
        }
    }

    Optional<Edge> edge(String archiId) {
        return Optional.ofNullable(edges.get(archiId));
    }

    private void edge(DocumentNode connection, int position) {
        Optional<DocumentNode> relationship = connection.attribute("archimateRelationship").map(concepts::get)
                .filter(r -> r.kind() == DocumentNode.Kind.RELATIONSHIP);
        String archiType = relationship.flatMap(DocumentNode::archiType)
                .orElse(connection.archiType().orElse(""));
        List<Bendpoint> bendpoints = new ArrayList<>();
        for (DocumentValue value : connection.values("bendpoint")) {
            bendpoints.add(new Bendpoint(number(value, "startX"), number(value, "startY"), number(value, "endX"),
                    number(value, "endY")));
        }
        String id = connection.archiId().value();
        edges.put(id, new Edge(id, archiType,
                relationship.flatMap(r -> r.attribute("accessType")).flatMap(AccessType::fromFileValue).orElse(null),
                relationship.flatMap(r -> r.attribute("directed")).filter("true"::equals).isPresent(),
                relationship.flatMap(r -> r.attribute("name")).filter(n -> !n.isEmpty()).orElse(null),
                connection.attribute("source").orElse(""), connection.attribute("target").orElse(""),
                bendpoints, Style.of(connection), position, index++));
        List<DocumentContent> content = connection.content();
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i) instanceof DocumentNode inner && inner.tag().equals("sourceConnection")) {
                edge(inner, i);
            }
        }
    }

    /** {@code x, y, width, height}; ширины и высоты нет — {@code -1}, «по умолчанию для типа». */
    private static double[] bounds(DocumentNode node) {
        return node.values("bounds").stream().findFirst()
                .map(b -> new double[] {number(b, "x"), number(b, "y"), numberOr(b, "width", -1),
                        numberOr(b, "height", -1)})
                .orElse(new double[] {0, 0, -1, -1});
    }

    private static double number(DocumentValue value, String name) {
        return numberOr(value, name, 0);
    }

    private static double numberOr(DocumentValue value, String name, double fallback) {
        return value.attribute(name).flatMap(ViewScene::integer).map(Integer::doubleValue).orElse(fallback);
    }

    private static Optional<Integer> integer(String value) {
        try {
            return Optional.of(Integer.parseInt(value.strip()));
        } catch (NumberFormatException notNumber) {
            return Optional.empty();
        }
    }

    private static Optional<String> nonBlank(DocumentNode node, String attribute) {
        return node.attribute(attribute).filter(v -> !v.isBlank());
    }

    private static boolean isElement(DocumentNode node) {
        return node.kind() == DocumentNode.Kind.ELEMENT && node.archiType().isPresent();
    }
}
