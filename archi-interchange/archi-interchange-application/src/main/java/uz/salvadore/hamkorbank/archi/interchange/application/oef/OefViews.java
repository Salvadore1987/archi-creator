package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.attr;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.close;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.endpointsClosure;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.indent;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.integer;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.langString;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.number;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.ref;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.size;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.union;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Size;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Bendpoint;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Link;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Point;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Rect;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode.ViewObject;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;

/** Представления-диаграммы в OEF: узлы с абсолютной геометрией, линии и точки перегиба. */
final class OefViews {

    /** Точки зрения Archi по имени в файле — их имена в перечне OEF; прочие пишутся как есть. */
    private static final Map<String, String> VIEWPOINTS = Map.ofEntries(
            Map.entry("application_cooperation", "Application Cooperation"),
            Map.entry("application_usage", "Application Usage"),
            Map.entry("business_process_cooperation", "Business Process Cooperation"),
            Map.entry("capability", "Capability Map"),
            Map.entry("goal_realization", "Goal Realization"),
            Map.entry("implementation_deployment", "Implementation and Deployment"),
            Map.entry("implementation_migration", "Implementation and Migration"),
            Map.entry("information_structure", "Information Structure"),
            Map.entry("layered", "Layered"),
            Map.entry("migration", "Migration"),
            Map.entry("motivation", "Motivation"),
            Map.entry("organization", "Organization"),
            Map.entry("outcome_realization", "Outcome Realization"),
            Map.entry("physical", "Physical"),
            Map.entry("product", "Product"),
            Map.entry("project", "Project"),
            Map.entry("requirements_realization", "Requirements Realization"),
            Map.entry("resource", "Resource Map"),
            Map.entry("service_realization", "Service Realization"),
            Map.entry("stakeholder", "Stakeholder"),
            Map.entry("strategy", "Strategy"),
            Map.entry("technology", "Technology"),
            Map.entry("technology_usage", "Technology Usage"),
            Map.entry("value_stream", "Value Stream"));

    /** Атрибуты стиля, у которых в OEF есть место. */
    private static final Set<String> STYLE_ATTRIBUTES = Set.of("fillColor", "alpha", "lineColor", "lineAlpha", "font",
            "fontColor", "lineWidth");

    private static final Set<String> VIEW_ATTRIBUTES = Set.of(OefMarkup.XSI_TYPE, "name", "id", "viewpoint");
    private static final Set<String> NODE_ATTRIBUTES = union(STYLE_ATTRIBUTES, OefMarkup.XSI_TYPE, "id",
            "targetConnections");
    private static final Set<String> EDGE_ATTRIBUTES = union(STYLE_ATTRIBUTES, OefMarkup.XSI_TYPE, "id", "source",
            "target", "targetConnections");

    private final OefPass pass;
    private final Notation notation;
    private final OefStyle style;

    OefViews(OefPass pass, Notation notation) {
        this.pass = pass;
        this.notation = notation;
        this.style = new OefStyle(pass);
    }

    /** Раздел {@code views} файла; представление не-диаграмма — запись отчёта. */
    StringBuilder write() {
        StringBuilder out = new StringBuilder();
        for (DocumentNode view : pass.document().views()) {
            String id = view.archiId().value();
            if (!pass.diagrams().contains(id)) {
                pass.loss(id, "view", "type",
                        Message.of(InterchangeMessages.OEF_LOSS_VIEW_TYPE, view.archiType().orElse("")));
                continue;
            }
            new ViewRun(view).write(out);
        }
        if (out.isEmpty()) {
            return out;
        }
        return new StringBuilder("  <views>\n    <diagrams>\n").append(out).append("    </diagrams>\n  </views>\n");
    }

    /** Вид узла представления в OEF. */
    private enum NodeKind {
        ELEMENT("Element"),
        CONTAINER("Container"),
        LABEL("Label");

        private final String oef;

        NodeKind(String oef) {
            this.oef = oef;
        }
    }

    /** Узел, который попадёт в файл: абсолютный прямоугольник и вложенные узлы. */
    private record OutNode(DocumentNode node, NodeKind kind, Rect rect, List<OutNode> children) {
    }

    private final class ViewRun {

        private final DocumentNode view;
        private final String viewId;
        private final List<OutNode> roots;
        private final Map<String, Rect> rects = new HashMap<>();
        private final List<DocumentNode> connections = new ArrayList<>();
        private final Map<String, Link> links = new HashMap<>();
        private double shiftX;
        private double shiftY;

        ViewRun(DocumentNode view) {
            this.view = view;
            this.viewId = view.archiId().value();
            this.roots = nodes(view, 0, 0);
        }

        void write(StringBuilder out) {
            pass.checkAttributes(view, "view", VIEW_ATTRIBUTES);
            pass.checkContent(view, "view", Set.of("documentation", "property"));
            Map<String, DocumentNode> candidates = new LinkedHashMap<>();
            for (DocumentNode connection : connections) {
                connectionCandidate(connection).ifPresent(c -> candidates.put(c.archiId().value(), c));
            }
            Set<String> kept = endpointsClosure(candidates, rects.keySet());
            List<DocumentNode> exported = new ArrayList<>();
            for (DocumentNode connection : candidates.values()) {
                if (kept.contains(connection.archiId().value())) {
                    exported.add(connection);
                } else {
                    pass.loss(connection.archiId().value(), "edge", "endpoint",
                            Message.of(InterchangeMessages.OEF_LOSS_EDGE_ENDPOINT));
                }
            }
            Map<String, List<Point>> bendpoints = new HashMap<>();
            exported.forEach(c -> bendpoints.put(c.archiId().value(), bendpoints(c)));
            shift(bendpoints);

            out.append("      <view identifier=\"").append(attr(pass.identifier(viewId, "view")))
                    .append("\" xsi:type=\"Diagram\"");
            view.attribute("viewpoint").filter(v -> !v.isEmpty()).ifPresent(v -> out.append(" viewpoint=\"")
                    .append(attr(VIEWPOINTS.getOrDefault(v, v))).append('"'));
            out.append(">\n");
            langString(out, 4, "name", view.attribute("name").orElse(""));
            pass.documentation(out, 4, view);
            pass.properties(out, 4, view.values("property"));
            roots.forEach(n -> node(out, n, 4));
            for (DocumentNode connection : exported) {
                connection(out, connection, bendpoints.getOrDefault(connection.archiId().value(), List.of()));
            }
            out.append("      </view>\n");
        }

        /**
         * Узлы уровня. Узел без аналога в OEF не выгружается, а его вложенные
         * поднимаются к ближайшему выгруженному предку: координаты абсолютные,
         * картинка не меняется.
         */
        private List<OutNode> nodes(DocumentNode parent, double parentX, double parentY) {
            List<OutNode> level = new ArrayList<>();
            for (DocumentNode child : parent.children()) {
                if (child.tag().equals("sourceConnection")) {
                    collectConnection(child);
                    continue;
                }
                if (!child.tag().equals("child")) {
                    continue;
                }
                String id = child.archiId().value();
                Rect rect = rect(child, parentX, parentY);
                List<OutNode> inner = nodes(child, rect.x(), rect.y());
                Optional<NodeKind> kind = nodeKind(child);
                if (kind.isEmpty()) {
                    level.addAll(inner);
                    continue;
                }
                rects.put(id, rect);
                if (kind.get() == NodeKind.LABEL) {
                    level.add(new OutNode(child, kind.get(), rect, List.of()));
                    level.addAll(inner);
                } else {
                    level.add(new OutNode(child, kind.get(), rect, inner));
                }
            }
            return level;
        }

        private void collectConnection(DocumentNode connection) {
            connections.add(connection);
            links.put(connection.archiId().value(), new Link(connection.attribute("source").orElse(""),
                    connection.attribute("target").orElse(""), relativeBendpoints(connection)));
            connection.children().stream().filter(c -> c.tag().equals("sourceConnection"))
                    .forEach(this::collectConnection);
        }

        /** Вид узла; узел без аналога — запись отчёта и пусто. */
        private Optional<NodeKind> nodeKind(DocumentNode node) {
            String id = node.archiId().value();
            String xsiType = node.archiType().orElse("");
            return switch (node.viewObject()) {
                case ELEMENT -> {
                    boolean exported = node.attribute("archimateElement").filter(pass.concepts()::contains)
                            .map(pass.byId()::get).filter(e -> e.kind() != DocumentNode.Kind.RELATIONSHIP)
                            .isPresent();
                    if (!exported) {
                        pass.loss(id, "node", "element", Message.of(InterchangeMessages.OEF_LOSS_NODE_CONCEPT));
                    }
                    yield exported ? Optional.of(NodeKind.ELEMENT) : Optional.empty();
                }
                case GROUP -> Optional.of(NodeKind.CONTAINER);
                case NOTE, VIEW_REFERENCE -> Optional.of(NodeKind.LABEL);
                default -> {
                    pass.loss(id, "node", "type", Message.of(InterchangeMessages.OEF_LOSS_NODE_TYPE, xsiType));
                    yield Optional.empty();
                }
            };
        }

        private Rect rect(DocumentNode node, double parentX, double parentY) {
            Optional<DocumentValue> bounds = node.values("bounds").stream().findFirst();
            double x = bounds.map(b -> number(b, "x", 0)).orElse(0.0);
            double y = bounds.map(b -> number(b, "y", 0)).orElse(0.0);
            double width = bounds.map(b -> number(b, "width", -1)).orElse(-1.0);
            double height = bounds.map(b -> number(b, "height", -1)).orElse(-1.0);
            String shapeType = node.attribute("archimateElement").map(pass.byId()::get)
                    .flatMap(DocumentNode::archiType).orElse(node.archiType().orElse(""));
            Size size = notation.effectiveSize(shapeType, width, height);
            return new Rect(parentX + x, parentY + y, size.width(), size.height());
        }

        /** Линия, у которой есть аналог в OEF; прочие — запись отчёта и пусто. */
        private Optional<DocumentNode> connectionCandidate(DocumentNode connection) {
            String id = connection.archiId().value();
            String xsiType = connection.archiType().orElse("");
            switch (connection.viewObject()) {
                case RELATIONSHIP_LINE -> {
                    boolean exported = connection.attribute("archimateRelationship").filter(pass.concepts()::contains)
                            .isPresent();
                    if (!exported) {
                        pass.loss(id, "edge", "relationship", Message.of(InterchangeMessages.OEF_LOSS_EDGE_CONCEPT));
                        return Optional.empty();
                    }
                    return Optional.of(connection);
                }
                case LINE -> {
                    return Optional.of(connection);
                }
                default -> {
                    pass.loss(id, "edge", "type", Message.of(InterchangeMessages.OEF_LOSS_EDGE_TYPE, xsiType));
                    return Optional.empty();
                }
            }
        }

        /**
         * Точки перегиба в координатах холста. Конец, которого нет на представлении
         * (цепочка связей к связи длиннее предела или в цикле), — линия без них и запись отчёта.
         */
        private List<Point> bendpoints(DocumentNode connection) {
            List<Bendpoint> relative = relativeBendpoints(connection);
            if (relative.isEmpty()) {
                return List.of();
            }
            Optional<Rect> source = endpoint(connection.attribute("source").orElse(""));
            Optional<Rect> target = endpoint(connection.attribute("target").orElse(""));
            if (source.isEmpty() || target.isEmpty()) {
                pass.loss(connection.archiId().value(), "edge", "bendpoint",
                        Message.of(InterchangeMessages.OEF_LOSS_BENDPOINTS));
                return List.of();
            }
            return Routing.absoluteBendpoints(source.get(), target.get(), relative);
        }

        private Optional<Rect> endpoint(String id) {
            return Routing.endpoint(id, rects, links);
        }

        /** Сдвиг представления так, чтобы ни одна координата не ушла в минус. */
        private void shift(Map<String, List<Point>> bendpoints) {
            double minX = 0;
            double minY = 0;
            for (Rect rect : rects.values()) {
                minX = Math.min(minX, rect.x());
                minY = Math.min(minY, rect.y());
            }
            for (List<Point> points : bendpoints.values()) {
                for (Point point : points) {
                    minX = Math.min(minX, point.x());
                    minY = Math.min(minY, point.y());
                }
            }
            shiftX = minX < 0 ? -Math.floor(minX) : 0;
            shiftY = minY < 0 ? -Math.floor(minY) : 0;
            if (shiftX > 0 || shiftY > 0) {
                pass.loss(viewId, "view", "coordinates", Message.of(InterchangeMessages.OEF_LOSS_SHIFT,
                        integer(shiftX), integer(shiftY)));
            }
        }

        private void node(StringBuilder out, OutNode node, int depth) {
            DocumentNode source = node.node();
            String id = source.archiId().value();
            ViewObject object = source.viewObject();
            Set<String> carried = switch (object) {
                case ELEMENT -> union(NODE_ATTRIBUTES, "archimateElement");
                case GROUP -> union(NODE_ATTRIBUTES, "name");
                case VIEW_REFERENCE -> union(NODE_ATTRIBUTES, "model");
                default -> NODE_ATTRIBUTES;
            };
            pass.checkAttributes(source, "node", carried);
            pass.checkContent(source, "node", object == ViewObject.NOTE
                    ? Set.of("bounds", "content", "documentation") : Set.of("bounds", "documentation"));

            Rect rect = node.rect();
            indent(out, depth).append("<node identifier=\"").append(attr(pass.identifier(id, "node")))
                    .append("\" xsi:type=\"").append(node.kind().oef).append('"');
            if (node.kind() == NodeKind.ELEMENT) {
                out.append(" elementRef=\"").append(attr(ref(source.attribute("archimateElement").orElseThrow())))
                        .append('"');
            }
            out.append(" x=\"").append(Math.round(rect.x() + shiftX)).append("\" y=\"")
                    .append(Math.round(rect.y() + shiftY)).append("\" w=\"").append(size(rect.width()))
                    .append("\" h=\"").append(size(rect.height())).append('"');

            StringBuilder body = new StringBuilder();
            Optional<String> viewRef = Optional.empty();
            switch (object) {
                case GROUP -> source.attribute("name").filter(n -> !n.isEmpty())
                        .ifPresent(n -> langString(body, depth + 1, "label", n));
                case NOTE -> source.values("content").stream().findFirst()
                        .map(DocumentValue::text).filter(t -> !t.isEmpty())
                        .ifPresent(t -> langString(body, depth + 1, "label", t));
                case VIEW_REFERENCE -> {
                    Optional<String> target = source.attribute("model");
                    target.map(pass.byId()::get).flatMap(v -> v.attribute("name")).filter(n -> !n.isEmpty())
                            .ifPresent(n -> langString(body, depth + 1, "label", n));
                    viewRef = target.filter(pass.diagrams()::contains);
                    if (viewRef.isEmpty()) {
                        pass.loss(id, "node", "viewRef", Message.of(InterchangeMessages.OEF_LOSS_VIEW_REF));
                    }
                }
                default -> {
                }
            }
            pass.documentation(body, depth + 1, source);
            style.style(body, source, "node", depth + 1);
            viewRef.ifPresent(v -> indent(body, depth + 1).append("<viewRef ref=\"").append(attr(ref(v)))
                    .append("\"/>\n"));
            node.children().forEach(child -> node(body, child, depth + 1));
            close(out, body, depth, "node");
        }

        private void connection(StringBuilder out, DocumentNode connection, List<Point> points) {
            String id = connection.archiId().value();
            boolean relationship = connection.viewObject() == ViewObject.RELATIONSHIP_LINE;
            pass.checkAttributes(connection, "edge", relationship ? union(EDGE_ATTRIBUTES, "archimateRelationship")
                    : union(EDGE_ATTRIBUTES, "name"));
            pass.checkContent(connection, "edge", Set.of("bendpoint", "documentation"));

            out.append("        <connection identifier=\"").append(attr(pass.identifier(id, "edge")))
                    .append("\" xsi:type=\"").append(relationship ? "Relationship" : "Line").append('"');
            if (relationship) {
                out.append(" relationshipRef=\"")
                        .append(attr(ref(connection.attribute("archimateRelationship").orElseThrow()))).append('"');
            }
            out.append(" source=\"").append(attr(ref(connection.attribute("source").orElse(""))))
                    .append("\" target=\"").append(attr(ref(connection.attribute("target").orElse(""))))
                    .append('"');
            StringBuilder body = new StringBuilder();
            if (!relationship) {
                connection.attribute("name").filter(n -> !n.isEmpty())
                        .ifPresent(n -> langString(body, 5, "label", n));
            }
            pass.documentation(body, 5, connection);
            style.style(body, connection, "edge", 5);
            for (Point point : points) {
                indent(body, 5).append("<bendpoint x=\"").append(Math.round(point.x() + shiftX))
                        .append("\" y=\"").append(Math.round(point.y() + shiftY)).append("\"/>\n");
            }
            close(out, body, 4, "connection");
        }
    }

    private static List<Bendpoint> relativeBendpoints(DocumentNode connection) {
        List<Bendpoint> relative = new ArrayList<>();
        for (DocumentValue value : connection.values("bendpoint")) {
            relative.add(new Bendpoint(number(value, "startX", 0), number(value, "startY", 0),
                    number(value, "endX", 0), number(value, "endY", 0)));
        }
        return relative;
    }
}
