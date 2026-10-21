package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Size;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Bendpoint;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Link;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Point;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Rect;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.XmlEscaping;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossObjectKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;

/**
 * Документ {@code .archimate} в ArchiMate Model Exchange File Format 3.1 и отчёт о том,
 * что не перенеслось.
 *
 * <p>Переносится всё, что формат умеет выразить, независимо от того, поддержан ли тип
 * в редакторе: у {@code Location} или {@code Grouping} в OEF есть свои типы. Не переносится
 * то, чего в OEF нет, — остаток XML, представления не-ArchiMate, часть оформления.
 *
 * <p>Отчёт полон по построению: у каждого вида объекта закрытый список атрибутов
 * и значений, которые писатель переносит, и всё, что в него не входит, становится
 * записью отчёта. Атрибут, который Archi заведёт завтра, окажется в отчёте сам.
 *
 * <p>Геометрия в OEF абсолютная: {@code bounds} складываются от корня представления,
 * размер {@code -1} подставляется по типу, как на канве, точки перегиба переводятся
 * из смещений от центров в координаты той же формулой, что у канвы. Отрицательных
 * координат формат не допускает — такое представление сдвигается целиком.
 *
 * <p>Результат детерминирован: один документ — одни и те же байты.
 */
public final class OefWriter {

    /** Язык текстов модели: формат многоязычен, мы пишем один язык. */
    public static final String LANGUAGE = "ru";

    private static final String XSI_TYPE = "xsi:type";
    private static final String PREFIX = "archimate:";
    private static final String DIAGRAM = "archimate:ArchimateDiagramModel";
    private static final String JUNCTION = "archimate:Junction";
    private static final String RELATIONSHIP_SUFFIX = "Relationship";

    /** Типы элементов OEF 3.1 без соединителей — перечень схемы. */
    private static final Set<String> ELEMENT_TYPES = Set.of("BusinessActor", "BusinessRole",
            "BusinessCollaboration", "BusinessInterface", "BusinessProcess", "BusinessFunction", "BusinessInteraction",
            "BusinessEvent", "BusinessService", "BusinessObject", "Contract", "Representation", "Product",
            "ApplicationComponent", "ApplicationCollaboration", "ApplicationInterface", "ApplicationFunction",
            "ApplicationInteraction", "ApplicationProcess", "ApplicationEvent", "ApplicationService", "DataObject",
            "Node", "Device", "SystemSoftware", "TechnologyCollaboration", "TechnologyInterface", "Path",
            "CommunicationNetwork", "TechnologyFunction", "TechnologyProcess", "TechnologyInteraction",
            "TechnologyEvent", "TechnologyService", "Artifact", "Equipment", "Facility", "DistributionNetwork",
            "Material", "Stakeholder", "Driver", "Assessment", "Goal", "Outcome", "Principle", "Requirement",
            "Constraint", "Meaning", "Value", "Resource", "Capability", "CourseOfAction", "ValueStream", "WorkPackage",
            "Deliverable", "ImplementationEvent", "Plateau", "Gap", "Grouping", "Location");

    private static final Set<String> RELATIONSHIP_TYPES = Set.of("Composition", "Aggregation", "Assignment",
            "Realization", "Serving", "Access", "Influence", "Triggering", "Flow", "Specialization", "Association");

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

    private static final Map<AccessType, String> ACCESS = Map.of(AccessType.WRITE, "Write", AccessType.READ, "Read",
            AccessType.ACCESS, "Access", AccessType.READ_WRITE, "ReadWrite");

    /** Атрибуты стиля, у которых в OEF есть место. */
    private static final Set<String> STYLE = Set.of("fillColor", "alpha", "lineColor", "lineAlpha", "font",
            "fontColor", "lineWidth");

    private static final Set<String> MODEL_ATTRIBUTES = Set.of("name", "id", "version");
    private static final Set<String> FOLDER_ATTRIBUTES = Set.of("name", "id");
    private static final Set<String> ELEMENT_ATTRIBUTES = Set.of(XSI_TYPE, "name", "id");
    private static final Set<String> VIEW_ATTRIBUTES = Set.of(XSI_TYPE, "name", "id", "viewpoint");
    private static final Set<String> NODE_ATTRIBUTES = union(STYLE, XSI_TYPE, "id", "targetConnections");
    private static final Set<String> EDGE_ATTRIBUTES = union(STYLE, XSI_TYPE, "id", "source", "target",
            "targetConnections");

    private static final Pattern NCNAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*$");
    private static final Pattern COLOR = Pattern.compile("^#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})$");
    private static final Pattern FRAGMENT_TAG = Pattern.compile("^<([^\\s/>]+)");

    private final Notation notation;

    /** @param notation размеры по умолчанию для типов — те же, что у канвы */
    public OefWriter(Notation notation) {
        this.notation = notation;
    }

    /** Файл OEF в UTF-8 и всё, что в него не перенеслось, в порядке обхода документа. */
    public record Written(byte[] xml, List<OefLoss> losses) {

        public Written {
            xml = xml.clone();
            losses = List.copyOf(losses);
        }

        @Override
        public byte[] xml() {
            return xml.clone();
        }
    }

    /** Документ модели в Open Exchange Format вместе с отчётом о потерях. */
    public Written write(ModelDocument document) {
        return new Run(document).write();
    }

    /** Один проход писателя: идентификаторы, словарь свойств и отчёт копятся здесь. */
    private final class Run {

        private final ModelDocument document;
        private final String modelId;
        private final List<OefLoss> losses = new ArrayList<>();
        private final Map<String, String> propertyIds = new LinkedHashMap<>();
        private final Set<String> usedIds = new HashSet<>();
        private final Set<String> concepts = new HashSet<>();
        private final Set<String> diagrams = new HashSet<>();
        private final Map<String, DocumentNode> byId = new HashMap<>();
        private int nextProperty = 1;

        Run(ModelDocument document) {
            this.document = document;
            this.modelId = document.archiId().value();
            usedIds.add(ref(modelId));
            for (DocumentNode node : document.allNodes()) {
                byId.put(node.archiId().value(), node);
                usedIds.add(ref(node.archiId().value()));
            }
        }

        Written write() {
            StringBuilder model = new StringBuilder();
            model.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            model.append("<model xmlns=\"").append(OefSchema.NAMESPACE)
                    .append("\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"")
                    .append(OefSchema.NAMESPACE).append(' ').append(OefSchema.LOCATION).append("\" identifier=\"")
                    .append(attr(identifier(modelId, "model"))).append("\">\n");
            modelHeader(model);

            StringBuilder elements = elements();
            StringBuilder relationships = relationships();
            document.views().stream().filter(v -> v.archiType().filter(DIAGRAM::equals).isPresent())
                    .forEach(v -> diagrams.add(v.archiId().value()));
            StringBuilder views = views();
            StringBuilder organizations = organizations();

            model.append(elements).append(relationships).append(organizations);
            if (!propertyIds.isEmpty()) {
                model.append("  <propertyDefinitions>\n");
                propertyIds.forEach((key, id) -> {
                    model.append("    <propertyDefinition identifier=\"").append(attr(id))
                            .append("\" type=\"string\">\n");
                    langString(model, 3, "name", key);
                    model.append("    </propertyDefinition>\n");
                });
                model.append("  </propertyDefinitions>\n");
            }
            model.append(views).append("</model>\n");
            return new Written(model.toString().getBytes(StandardCharsets.UTF_8), losses);
        }

        // ── модель ───────────────────────────────────────────────────────

        private void modelHeader(StringBuilder out) {
            for (Attribute attribute : document.attributes()) {
                if (!MODEL_ATTRIBUTES.contains(attribute.name()) && !attribute.name().startsWith("xmlns")) {
                    attributeLoss(modelId, "model", attribute);
                }
            }
            langString(out, 1, "name", document.name());
            document.documentation().ifPresent(text -> langString(out, 1, "documentation", text));
            List<DocumentValue> properties = new ArrayList<>();
            for (DocumentContent item : document.content()) {
                switch (item) {
                    case DocumentValue value when value.tag().equals("property") -> properties.add(value);
                    case DocumentValue value when value.tag().equals("purpose") -> {
                    }
                    case DocumentValue value -> loss(modelId, "model", "value:" + value.tag(),
                            Message.of(InterchangeMessages.OEF_LOSS_VALUE, value.tag()));
                    case RawXmlFragment raw -> rawLoss(modelId, "model", raw);
                    case DocumentNode node -> {
                    }
                }
            }
            properties(out, 1, properties);
        }

        // ── элементы и связи ─────────────────────────────────────────────

        private StringBuilder elements() {
            StringBuilder out = new StringBuilder();
            for (DocumentNode element : document.elements()) {
                String id = element.archiId().value();
                Optional<String> type = elementType(element);
                if (type.isEmpty()) {
                    loss(id, "element", "type",
                            Message.of(InterchangeMessages.OEF_LOSS_CONCEPT_TYPE, element.archiType().orElse("")));
                    continue;
                }
                concepts.add(id);
                Set<String> carried = element.archiType().filter(JUNCTION::equals).isPresent()
                        ? union(ELEMENT_ATTRIBUTES, "type") : ELEMENT_ATTRIBUTES;
                checkAttributes(element, "element", carried);
                out.append("    <element identifier=\"").append(attr(identifier(id, "element")))
                        .append("\" xsi:type=\"").append(type.get()).append("\">\n");
                langString(out, 3, "name", element.attribute("name").orElse(""));
                conceptBody(out, element, "element");
                out.append("    </element>\n");
            }
            return section("elements", out);
        }

        private StringBuilder relationships() {
            Map<String, DocumentNode> candidates = new LinkedHashMap<>();
            for (DocumentNode relationship : document.relationships()) {
                String id = relationship.archiId().value();
                if (relationshipType(relationship).isEmpty()) {
                    loss(id, "relationship", "type", Message.of(InterchangeMessages.OEF_LOSS_CONCEPT_TYPE,
                            relationship.archiType().orElse("")));
                } else {
                    candidates.put(id, relationship);
                }
            }
            Set<String> kept = endpointsClosure(candidates, concepts);
            StringBuilder out = new StringBuilder();
            for (DocumentNode relationship : candidates.values()) {
                String id = relationship.archiId().value();
                if (!kept.contains(id)) {
                    loss(id, "relationship", "endpoint", Message.of(InterchangeMessages.OEF_LOSS_ENDPOINT));
                    continue;
                }
                concepts.add(id);
                String type = relationshipType(relationship).orElseThrow();
                Set<String> carried = union(ELEMENT_ATTRIBUTES, "source", "target");
                StringBuilder extra = new StringBuilder();
                switch (type) {
                    case "Access" -> {
                        carried = union(carried, "accessType");
                        Optional<String> value = relationship.attribute("accessType");
                        Optional<AccessType> access = value.isEmpty() ? Optional.of(AccessType.WRITE)
                                : AccessType.fromFileValue(value.get());
                        if (access.isPresent()) {
                            extra.append(" accessType=\"").append(ACCESS.get(access.get())).append('"');
                        } else {
                            attributeLoss(id, "relationship", new Attribute("accessType", value.get()));
                        }
                    }
                    case "Association" -> {
                        carried = union(carried, "directed");
                        if (relationship.attribute("directed").filter("true"::equals).isPresent()) {
                            extra.append(" isDirected=\"true\"");
                        }
                    }
                    case "Influence" -> {
                        carried = union(carried, "strength");
                        relationship.attribute("strength").filter(s -> !s.isEmpty())
                                .ifPresent(s -> extra.append(" modifier=\"").append(attr(s)).append('"'));
                    }
                    default -> {
                    }
                }
                checkAttributes(relationship, "relationship", carried);
                out.append("    <relationship identifier=\"").append(attr(identifier(id, "relationship")))
                        .append("\" xsi:type=\"").append(type).append("\" source=\"")
                        .append(attr(ref(relationship.attribute("source").orElse("")))).append("\" target=\"")
                        .append(attr(ref(relationship.attribute("target").orElse("")))).append('"').append(extra);
                StringBuilder body = new StringBuilder();
                relationship.attribute("name").filter(n -> !n.isEmpty()).ifPresent(n -> langString(body, 3, "name", n));
                conceptBody(body, relationship, "relationship");
                close(out, body, 2, "relationship");
            }
            return section("relationships", out);
        }

        /** Документация и свойства объекта модели; прочее содержимое — в отчёт. */
        private void conceptBody(StringBuilder out, DocumentNode node, String kind) {
            documentation(out, 3, node);
            checkContent(node, kind, Set.of("documentation", "property"));
            properties(out, 3, node.values("property"));
        }

        private Optional<String> elementType(DocumentNode element) {
            String xsiType = element.archiType().orElse("");
            if (xsiType.equals(JUNCTION)) {
                return Optional.of(element.attribute("type").filter("or"::equals).isPresent() ? "OrJunction"
                        : "AndJunction");
            }
            return xsiType.startsWith(PREFIX) ? Optional.of(xsiType.substring(PREFIX.length()))
                    .filter(ELEMENT_TYPES::contains) : Optional.empty();
        }

        private Optional<String> relationshipType(DocumentNode relationship) {
            String xsiType = relationship.archiType().orElse("");
            if (!xsiType.startsWith(PREFIX) || !xsiType.endsWith(RELATIONSHIP_SUFFIX)) {
                return Optional.empty();
            }
            return Optional.of(xsiType.substring(PREFIX.length(), xsiType.length() - RELATIONSHIP_SUFFIX.length()))
                    .filter(RELATIONSHIP_TYPES::contains);
        }

        // ── папки ────────────────────────────────────────────────────────

        private StringBuilder organizations() {
            StringBuilder out = new StringBuilder();
            for (DocumentNode folder : document.folders()) {
                folder(out, folder, 2, true);
            }
            if (out.isEmpty()) {
                return out;
            }
            return new StringBuilder("  <organizations>\n").append(out).append("  </organizations>\n");
        }

        private void folder(StringBuilder out, DocumentNode folder, int depth, boolean top) {
            String id = folder.archiId().value();
            Set<String> carried = FOLDER_ATTRIBUTES;
            if (top) {
                carried = union(carried, "type");
                folder.attribute("type").ifPresent(type -> loss(id, "folder", "type",
                        Message.of(InterchangeMessages.OEF_LOSS_FOLDER_TYPE, type)));
            }
            checkAttributes(folder, "folder", carried);
            checkContent(folder, "folder", Set.of("documentation"));
            indent(out, depth).append("<item identifier=\"").append(attr(identifier(id, "folder"))).append("\">\n");
            folder.attribute("name").filter(n -> !n.isEmpty()).ifPresent(n -> langString(out, depth + 1, "label", n));
            documentation(out, depth + 1, folder);
            for (DocumentNode child : folder.children()) {
                String childId = child.archiId().value();
                if (child.tag().equals("folder")) {
                    folder(out, child, depth + 1, false);
                } else if (concepts.contains(childId) || diagrams.contains(childId)) {
                    indent(out, depth + 1).append("<item identifierRef=\"").append(attr(ref(childId))).append("\"/>\n");
                }
            }
            indent(out, depth).append("</item>\n");
        }

        // ── представления ────────────────────────────────────────────────

        private StringBuilder views() {
            StringBuilder out = new StringBuilder();
            for (DocumentNode view : document.views()) {
                String id = view.archiId().value();
                if (!diagrams.contains(id)) {
                    loss(id, "view", "type",
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
                checkAttributes(view, "view", VIEW_ATTRIBUTES);
                checkContent(view, "view", Set.of("documentation", "property"));
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
                        loss(connection.archiId().value(), "edge", "endpoint",
                                Message.of(InterchangeMessages.OEF_LOSS_EDGE_ENDPOINT));
                    }
                }
                Map<String, List<Point>> bendpoints = new HashMap<>();
                exported.forEach(c -> bendpoints.put(c.archiId().value(), bendpoints(c)));
                shift(bendpoints);

                out.append("      <view identifier=\"").append(attr(identifier(viewId, "view")))
                        .append("\" xsi:type=\"Diagram\"");
                view.attribute("viewpoint").filter(v -> !v.isEmpty()).ifPresent(v -> out.append(" viewpoint=\"")
                        .append(attr(VIEWPOINTS.getOrDefault(v, v))).append('"'));
                out.append(">\n");
                langString(out, 4, "name", view.attribute("name").orElse(""));
                documentation(out, 4, view);
                properties(out, 4, view.values("property"));
                roots.forEach(n -> node(out, n, 4));
                for (DocumentNode connection : exported) {
                    connection(out, connection, bendpoints.get(connection.archiId().value()));
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
                return switch (xsiType) {
                    case "archimate:DiagramObject" -> {
                        boolean exported = node.attribute("archimateElement").filter(concepts::contains)
                                .filter(e -> !byId.get(e).archiType().orElse("").endsWith(RELATIONSHIP_SUFFIX))
                                .isPresent();
                        if (!exported) {
                            loss(id, "node", "element", Message.of(InterchangeMessages.OEF_LOSS_NODE_CONCEPT));
                        }
                        yield exported ? Optional.of(NodeKind.ELEMENT) : Optional.empty();
                    }
                    case "archimate:Group" -> Optional.of(NodeKind.CONTAINER);
                    case "archimate:Note", "archimate:DiagramModelReference" -> Optional.of(NodeKind.LABEL);
                    default -> {
                        loss(id, "node", "type", Message.of(InterchangeMessages.OEF_LOSS_NODE_TYPE, xsiType));
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
                String shapeType = node.attribute("archimateElement").map(byId::get)
                        .flatMap(DocumentNode::archiType).orElse(node.archiType().orElse(""));
                Size size = notation.effectiveSize(shapeType, width, height);
                return new Rect(parentX + x, parentY + y, size.width(), size.height());
            }

            /** Линия, у которой есть аналог в OEF; прочие — запись отчёта и пусто. */
            private Optional<DocumentNode> connectionCandidate(DocumentNode connection) {
                String id = connection.archiId().value();
                String xsiType = connection.archiType().orElse("");
                switch (xsiType) {
                    case "archimate:Connection" -> {
                        boolean exported = connection.attribute("archimateRelationship").filter(concepts::contains)
                                .isPresent();
                        if (!exported) {
                            loss(id, "edge", "relationship", Message.of(InterchangeMessages.OEF_LOSS_EDGE_CONCEPT));
                            return Optional.empty();
                        }
                        return Optional.of(connection);
                    }
                    case "archimate:DiagramModelConnection" -> {
                        return Optional.of(connection);
                    }
                    default -> {
                        loss(id, "edge", "type", Message.of(InterchangeMessages.OEF_LOSS_EDGE_TYPE, xsiType));
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
                    loss(connection.archiId().value(), "edge", "bendpoint",
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
                    loss(viewId, "view", "coordinates", Message.of(InterchangeMessages.OEF_LOSS_SHIFT,
                            integer(shiftX), integer(shiftY)));
                }
            }

            private void node(StringBuilder out, OutNode node, int depth) {
                DocumentNode source = node.node();
                String id = source.archiId().value();
                String xsiType = source.archiType().orElse("");
                Set<String> carried = switch (xsiType) {
                    case "archimate:DiagramObject" -> union(NODE_ATTRIBUTES, "archimateElement");
                    case "archimate:Group" -> union(NODE_ATTRIBUTES, "name");
                    case "archimate:DiagramModelReference" -> union(NODE_ATTRIBUTES, "model");
                    default -> NODE_ATTRIBUTES;
                };
                checkAttributes(source, "node", carried);
                checkContent(source, "node", xsiType.equals("archimate:Note")
                        ? Set.of("bounds", "content", "documentation") : Set.of("bounds", "documentation"));

                Rect rect = node.rect();
                indent(out, depth).append("<node identifier=\"").append(attr(identifier(id, "node")))
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
                switch (xsiType) {
                    case "archimate:Group" -> source.attribute("name").filter(n -> !n.isEmpty())
                            .ifPresent(n -> langString(body, depth + 1, "label", n));
                    case "archimate:Note" -> source.values("content").stream().findFirst()
                            .map(DocumentValue::text).filter(t -> !t.isEmpty())
                            .ifPresent(t -> langString(body, depth + 1, "label", t));
                    case "archimate:DiagramModelReference" -> {
                        Optional<String> target = source.attribute("model");
                        target.map(byId::get).flatMap(v -> v.attribute("name")).filter(n -> !n.isEmpty())
                                .ifPresent(n -> langString(body, depth + 1, "label", n));
                        viewRef = target.filter(diagrams::contains);
                        if (viewRef.isEmpty()) {
                            loss(id, "node", "viewRef", Message.of(InterchangeMessages.OEF_LOSS_VIEW_REF));
                        }
                    }
                    default -> {
                    }
                }
                documentation(body, depth + 1, source);
                style(body, source, "node", depth + 1);
                viewRef.ifPresent(v -> indent(body, depth + 1).append("<viewRef ref=\"").append(attr(ref(v)))
                        .append("\"/>\n"));
                node.children().forEach(child -> node(body, child, depth + 1));
                close(out, body, depth, "node");
            }

            private void connection(StringBuilder out, DocumentNode connection, List<Point> points) {
                String id = connection.archiId().value();
                boolean relationship = connection.archiType().filter("archimate:Connection"::equals).isPresent();
                checkAttributes(connection, "edge", relationship ? union(EDGE_ATTRIBUTES, "archimateRelationship")
                        : union(EDGE_ATTRIBUTES, "name"));
                checkContent(connection, "edge", Set.of("bendpoint", "documentation"));

                out.append("        <connection identifier=\"").append(attr(identifier(id, "edge")))
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
                documentation(body, 5, connection);
                style(body, connection, "edge", 5);
                for (Point point : points) {
                    indent(body, 5).append("<bendpoint x=\"").append(Math.round(point.x() + shiftX))
                            .append("\" y=\"").append(Math.round(point.y() + shiftY)).append("\"/>\n");
                }
                close(out, body, 4, "connection");
            }
        }

        // ── стиль ────────────────────────────────────────────────────────

        /** {@code style} из собственного оформления объекта; что не разобрано — в отчёт. */
        private void style(StringBuilder out, DocumentNode node, String kind, int depth) {
            String id = node.archiId().value();
            StringBuilder inner = new StringBuilder();
            color(inner, node, kind, "lineColor", "lineAlpha", depth + 1);
            color(inner, node, kind, "fillColor", "alpha", depth + 1);
            font(inner, node, kind, depth + 1);
            Optional<String> lineWidth = node.attribute("lineWidth");
            Optional<Integer> width = lineWidth.flatMap(OefWriter::parseInt).filter(w -> w > 0);
            if (lineWidth.isPresent() && width.isEmpty()) {
                styleLoss(id, kind, "lineWidth", lineWidth.get());
            }
            if (inner.isEmpty() && width.isEmpty()) {
                return;
            }
            indent(out, depth).append("<style");
            width.ifPresent(w -> out.append(" lineWidth=\"").append(w).append('"'));
            if (inner.isEmpty()) {
                out.append("/>\n");
            } else {
                out.append(">\n").append(inner);
                indent(out, depth).append("</style>\n");
            }
        }

        private void color(StringBuilder out, DocumentNode node, String kind, String colorName, String alphaName,
                           int depth) {
            String id = node.archiId().value();
            Optional<String> value = node.attribute(colorName).filter(v -> !v.isEmpty());
            Optional<int[]> rgb = value.flatMap(OefWriter::rgb);
            if (value.isPresent() && rgb.isEmpty()) {
                styleLoss(id, kind, colorName, value.get());
            }
            Optional<String> alpha = node.attribute(alphaName);
            Optional<Integer> percent = alpha.flatMap(OefWriter::parseInt).filter(a -> a >= 0 && a <= 255)
                    .map(a -> (int) Math.round(a * 100.0 / 255));
            if (alpha.isPresent() && percent.isEmpty()) {
                styleLoss(id, kind, alphaName, alpha.get());
            } else if (alpha.isPresent() && rgb.isEmpty()) {
                // Прозрачность в OEF — атрибут цвета: без собственного цвета её некуда записать.
                attributeLoss(id, kind, new Attribute(alphaName, alpha.get()));
            }
            rgb.ifPresent(c -> {
                indent(out, depth).append('<').append(colorName).append(rgbAttributes(c));
                percent.ifPresent(a -> out.append(" a=\"").append(a).append('"'));
                out.append("/>\n");
            });
        }

        /**
         * Шрифт Archi — строка {@code FontData} SWT: {@code 1|имя|кегль|начертание|…};
         * начертание — биты: 1 полужирный, 2 курсив.
         */
        private void font(StringBuilder out, DocumentNode node, String kind, int depth) {
            String id = node.archiId().value();
            Optional<String> font = node.attribute("font").filter(v -> !v.isEmpty());
            Optional<String> fontColor = node.attribute("fontColor").filter(v -> !v.isEmpty());
            Optional<int[]> color = fontColor.flatMap(OefWriter::rgb);
            if (fontColor.isPresent() && color.isEmpty()) {
                styleLoss(id, kind, "fontColor", fontColor.get());
            }
            StringBuilder attributes = new StringBuilder();
            if (font.isPresent()) {
                String[] parts = font.get().split("\\|");
                Optional<Double> height = parts.length > 3 ? parseDouble(parts[2]) : Optional.empty();
                Optional<Integer> bits = parts.length > 3 ? parseInt(parts[3]) : Optional.empty();
                if (height.isEmpty() || bits.isEmpty() || parts[1].isBlank()) {
                    styleLoss(id, kind, "font", font.get());
                } else {
                    attributes.append(" name=\"").append(attr(parts[1])).append('"');
                    double half = Math.round(height.get() * 2) / 2.0;
                    if (half >= 1) {
                        attributes.append(" size=\"").append(half == Math.floor(half) ? String.valueOf((long) half)
                                : String.valueOf(half)).append('"');
                    }
                    attributes.append(" style=\"").append(fontStyle(bits.get())).append('"');
                }
            }
            if (attributes.isEmpty() && color.isEmpty()) {
                return;
            }
            indent(out, depth).append("<font").append(attributes);
            if (color.isEmpty()) {
                out.append("/>\n");
                return;
            }
            out.append(">\n");
            indent(out, depth + 1).append("<color").append(rgbAttributes(color.get())).append("/>\n");
            indent(out, depth).append("</font>\n");
        }

        // ── общее ────────────────────────────────────────────────────────

        /** Каждый атрибут вне списка переносимых — запись отчёта. */
        private void checkAttributes(DocumentNode node, String kind, Set<String> carried) {
            for (Attribute attribute : node.attributes()) {
                if (!carried.contains(attribute.name()) && !attribute.name().startsWith("xmlns")) {
                    attributeLoss(node.archiId().value(), kind, attribute);
                }
            }
        }

        /** Остаток XML и значения вне списка переносимых — записи отчёта; вложенные узлы — не здесь. */
        private void checkContent(DocumentNode node, String kind, Set<String> carried) {
            String id = node.archiId().value();
            for (DocumentContent item : node.content()) {
                switch (item) {
                    case RawXmlFragment raw -> rawLoss(id, kind, raw);
                    case DocumentValue value when carried.contains(value.tag()) -> {
                    }
                    case DocumentValue value when value.tag().equals("property") -> {
                        String key = value.attribute("key").orElse("");
                        loss(id, kind, "property:" + key, Message.of(InterchangeMessages.OEF_LOSS_PROPERTY, key));
                    }
                    case DocumentValue value -> loss(id, kind, "value:" + value.tag(),
                            Message.of(InterchangeMessages.OEF_LOSS_VALUE, value.tag()));
                    case DocumentNode child -> {
                    }
                }
            }
        }

        private void properties(StringBuilder out, int depth, List<DocumentValue> properties) {
            if (properties.isEmpty()) {
                return;
            }
            indent(out, depth).append("<properties>\n");
            for (DocumentValue property : properties) {
                String key = property.attribute("key").orElse("");
                indent(out, depth + 1).append("<property propertyDefinitionRef=\"").append(attr(propertyId(key)))
                        .append("\">\n");
                langString(out, depth + 2, "value", property.attribute("value").orElse(""));
                indent(out, depth + 1).append("</property>\n");
            }
            indent(out, depth).append("</properties>\n");
        }

        /** Ключ свойства — запись общего словаря: {@code propid-N} в порядке первого появления. */
        private String propertyId(String key) {
            return propertyIds.computeIfAbsent(key, k -> {
                String id;
                do {
                    id = "propid-" + nextProperty++;
                } while (usedIds.contains(id));
                usedIds.add(id);
                return id;
            });
        }

        private void documentation(StringBuilder out, int depth, DocumentNode node) {
            node.documentation().filter(d -> !d.isEmpty()).ifPresent(d -> langString(out, depth, "documentation", d));
        }

        /**
         * Идентификатор объекта в файле. Не годный для {@code xs:ID} заменяется
         * производным от него самого — замена в отчёте; ссылки на объект берут ту же замену.
         */
        private String identifier(String archiId, String kind) {
            String id = ref(archiId);
            if (!id.equals(archiId)) {
                loss(archiId, kind, "identifier", Message.of(InterchangeMessages.OEF_LOSS_IDENTIFIER, archiId, id));
            }
            return id;
        }

        private void attributeLoss(String archiId, String kind, Attribute attribute) {
            loss(archiId, kind, "attribute:" + attribute.name(),
                    Message.of(InterchangeMessages.OEF_LOSS_ATTRIBUTE, attribute.name(), attribute.value()));
        }

        private void styleLoss(String archiId, String kind, String name, String value) {
            loss(archiId, kind, "style:" + name, Message.of(InterchangeMessages.OEF_LOSS_STYLE, name, value));
        }

        private void rawLoss(String archiId, String kind, RawXmlFragment raw) {
            Matcher tag = FRAGMENT_TAG.matcher(raw.xml());
            String name = tag.find() ? tag.group(1) : raw.xml();
            loss(archiId, kind, "xml:" + name, Message.of(InterchangeMessages.OEF_LOSS_RAW, name));
        }

        private void loss(String archiId, String kind, String aspect, Message reason) {
            losses.add(new OefLoss(archiId, LossObjectKind.of(kind), aspect, reason));
        }
    }

    // ── вне прохода ──────────────────────────────────────────────────────

    /** Множество связей, оба конца которых выгружены — среди уже известных или среди них самих. */
    private static Set<String> endpointsClosure(Map<String, DocumentNode> candidates, Set<String> known) {
        Set<String> kept = new LinkedHashSet<>(candidates.keySet());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (DocumentNode candidate : candidates.values()) {
                String id = candidate.archiId().value();
                if (!kept.contains(id)) {
                    continue;
                }
                String source = candidate.attribute("source").orElse("");
                String target = candidate.attribute("target").orElse("");
                boolean sourceKept = known.contains(source) || kept.contains(source);
                boolean targetKept = known.contains(target) || kept.contains(target);
                if (!sourceKept || !targetKept) {
                    kept.remove(id);
                    changed = true;
                }
            }
        }
        return kept;
    }

    /** Ссылка на объект: идентификатор как есть, если он NCName, иначе {@code id-} и 32 hex от него. */
    private static String ref(String archiId) {
        if (NCNAME.matcher(archiId).matches()) {
            return archiId;
        }
        return "id-" + ContentHash.of(archiId.getBytes(StandardCharsets.UTF_8)).value().substring(0, 32);
    }

    private static StringBuilder section(String tag, StringBuilder body) {
        if (body.isEmpty()) {
            return body;
        }
        return new StringBuilder("  <").append(tag).append(">\n").append(body).append("  </").append(tag).append(">\n");
    }

    private static void langString(StringBuilder out, int depth, String tag, String text) {
        indent(out, depth).append('<').append(tag).append(" xml:lang=\"").append(LANGUAGE).append("\">")
                .append(XmlEscaping.text(text)).append("</").append(tag).append(">\n");
    }

    private static void close(StringBuilder out, StringBuilder body, int depth, String tag) {
        if (body.isEmpty()) {
            out.append("/>\n");
        } else {
            out.append(">\n").append(body);
            indent(out, depth).append("</").append(tag).append(">\n");
        }
    }

    private static StringBuilder indent(StringBuilder out, int depth) {
        return out.append("  ".repeat(depth));
    }

    private static String attr(String value) {
        return XmlEscaping.attribute(value);
    }

    private static long size(double value) {
        return Math.max(1, Math.round(value));
    }

    private static String integer(double value) {
        return String.valueOf((long) value);
    }

    private static List<Bendpoint> relativeBendpoints(DocumentNode connection) {
        List<Bendpoint> relative = new ArrayList<>();
        for (DocumentValue value : connection.values("bendpoint")) {
            relative.add(new Bendpoint(number(value, "startX", 0), number(value, "startY", 0),
                    number(value, "endX", 0), number(value, "endY", 0)));
        }
        return relative;
    }

    private static double number(DocumentValue value, String name, double fallback) {
        return value.attribute(name).flatMap(OefWriter::parseInt).map(Integer::doubleValue).orElse(fallback);
    }

    private static Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Integer.parseInt(value.strip()));
        } catch (NumberFormatException notNumber) {
            return Optional.empty();
        }
    }

    private static Optional<Double> parseDouble(String value) {
        try {
            return Optional.of(Double.parseDouble(value.strip()));
        } catch (NumberFormatException notNumber) {
            return Optional.empty();
        }
    }

    private static Optional<int[]> rgb(String value) {
        Matcher m = COLOR.matcher(value.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[] {Integer.parseInt(m.group(1), 16), Integer.parseInt(m.group(2), 16),
                Integer.parseInt(m.group(3), 16)});
    }

    private static String rgbAttributes(int[] c) {
        return " r=\"" + c[0] + "\" g=\"" + c[1] + "\" b=\"" + c[2] + "\"";
    }

    private static String fontStyle(int bits) {
        List<String> styles = new ArrayList<>();
        if ((bits & 1) != 0) {
            styles.add("bold");
        }
        if ((bits & 2) != 0) {
            styles.add("italic");
        }
        return styles.isEmpty() ? "plain" : String.join(" ", styles).toLowerCase(Locale.ROOT);
    }

    private static Set<String> union(Set<String> base, String... more) {
        Set<String> all = new HashSet<>(base);
        all.addAll(List.of(more));
        return Set.copyOf(all);
    }
}
