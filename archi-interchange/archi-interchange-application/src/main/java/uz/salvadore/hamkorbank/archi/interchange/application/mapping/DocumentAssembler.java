package uz.salvadore.hamkorbank.archi.interchange.application.mapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attributes;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentOrder;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;
import uz.salvadore.hamkorbank.archi.interchange.domain.residue.NodeResidue;
import uz.salvadore.hamkorbank.archi.interchange.domain.residue.ResidueAttribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.residue.ResidueItem;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ConceptRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.StyleOverride;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/**
 * Агрегаты modeling — обратно в документ {@code .archimate} ({@code ModelDocumentAssembler}).
 *
 * <p>Столбцы накладываются на остаток и сильнее него: метка типизированного атрибута получает
 * значение из столбца, пропавшее значение убирается, новое встаёт туда, куда его поставил
 * бы Archi. Дочерние строки и записи остатка сливаются по общей разреженной нумерации.
 * {@code targetConnections} производен: множество — из рёбер, порядок — из остатка.
 */
public final class DocumentAssembler {

    public ModelDocument assemble(ModelContent content) {
        return new Run(content).assemble();
    }

    private static final class Run {

        private final ArchitectureModel model;
        private final List<View> views;
        private final Map<UUID, ArchiId> archiIds = new HashMap<>();
        private final Map<UUID, List<ViewEdge>> incoming = new HashMap<>();
        private final Map<UUID, Integer> edgeRank = new HashMap<>();

        Run(ModelContent content) {
            this.model = content.model();
            this.views = content.views();
            model.elements().values().forEach(e -> archiIds.put(e.id().value(), e.archiId()));
            model.relationships().values().forEach(r -> archiIds.put(r.id().value(), r.archiId()));
            for (View view : views) {
                view.nodes().values().forEach(n -> archiIds.put(n.id().value(), n.archiId()));
                view.edges().values().forEach(e -> {
                    archiIds.put(e.id().value(), e.archiId());
                    incoming.computeIfAbsent(e.target().value(), k -> new ArrayList<>()).add(e);
                });
                rankEdges(view);
            }
        }

        ModelDocument assemble() {
            ModelHeader header = model.header();
            NodeResidue residue = residue(header.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put("name", Optional.of(header.name()));
            typed.put("id", Optional.of(header.archiId().value()));
            typed.put("version", Optional.of(header.archiVersion()));
            List<Attribute> attributes = residue.empty() ? newModelAttributes(typed)
                    : attributes(residue, typed, Set.of("name"));
            Content content = new Content(residue);
            // У корня Archi пишет описание и свойства после папок, а не перед ними.
            content.single("purpose", header.documentation().map(d -> text("purpose", d)), XmlSchema.AFTER_ALL - 1);
            content.properties(header.properties(), XmlSchema.AFTER_ALL);
            model.folders().values().stream().filter(ModelFolder::root)
                    .forEach(f -> content.child(f.sortOrder().value(), folder(f)));
            return new ModelDocument(new Attributes(attributes), content.build(Optional.empty()));
        }

        // ── Дерево модели ───────────────────────────────────────────

        private DocumentNode folder(ModelFolder folder) {
            NodeResidue residue = residue(folder.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put("name", Optional.of(folder.name()));
            typed.put("id", Optional.of(folder.archiId().value()));
            typed.put("type", folder.folderType().map(t -> t.fileValue()));
            Content content = new Content(residue);
            FolderId id = folder.id();
            model.folders().values().stream().filter(f -> f.parentId().equals(Optional.of(id)))
                    .forEach(f -> content.child(f.sortOrder().value(), folder(f)));
            model.elements().values().stream().filter(e -> e.folderId().equals(id))
                    .forEach(e -> content.child(e.sortOrder().value(), element(e)));
            model.relationships().values().stream().filter(r -> r.folderId().equals(id))
                    .forEach(r -> content.child(r.sortOrder().value(), relationship(r)));
            views.stream().filter(v -> v.header().folderId().equals(id))
                    .forEach(v -> content.child(v.header().sortOrder().value(), view(v)));
            return node("folder", folder.archiId(), attributes(residue, typed, XmlSchema.FOLDER_ORDER, Set.of()),
                    content);
        }

        private DocumentNode element(Element element) {
            NodeResidue residue = residue(element.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(element.archiType().value()));
            typed.put("name", Optional.of(element.name()));
            typed.put("id", Optional.of(element.archiId().value()));
            Content content = new Content(residue);
            content.single("documentation", element.documentation().map(d -> text("documentation", d)),
                    XmlSchema.DOCUMENTATION_FIRST);
            content.properties(element.properties(), XmlSchema.PROPERTIES_FIRST);
            return node("element", element.archiId(),
                    attributes(residue, typed, XmlSchema.ELEMENT_ORDER, Set.of("name")), content);
        }

        private DocumentNode relationship(Relationship relationship) {
            NodeResidue residue = residue(relationship.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(relationship.archiType().value()));
            typed.put("name", relationship.name());
            typed.put("id", Optional.of(relationship.archiId().value()));
            typed.put("source", Optional.of(archiId(relationship.source())));
            typed.put("target", Optional.of(archiId(relationship.target())));
            typed.put("accessType", relationship.accessType().map(a -> String.valueOf(a.fileValue())));
            typed.put("directed", relationship.directed().map(String::valueOf));
            Content content = new Content(residue);
            content.single("documentation", relationship.documentation().map(d -> text("documentation", d)),
                    XmlSchema.DOCUMENTATION_FIRST);
            content.properties(relationship.properties(), XmlSchema.PROPERTIES_FIRST);
            return node("element", relationship.archiId(),
                    attributes(residue, typed, XmlSchema.RELATIONSHIP_ORDER, Set.of()), content);
        }

        // ── Представления ───────────────────────────────────────────

        private DocumentNode view(View view) {
            ViewHeader header = view.header();
            NodeResidue residue = residue(header.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(header.archiType().value()));
            typed.put("name", Optional.of(header.name()));
            typed.put("id", Optional.of(header.archiId().value()));
            typed.put("viewpoint", header.viewpoint());
            Content content = new Content(residue);
            content.single("documentation", header.documentation().map(d -> text("documentation", d)),
                    XmlSchema.DOCUMENTATION_FIRST);
            content.properties(header.properties(), XmlSchema.PROPERTIES_FIRST);
            view.nodes().values().stream().filter(n -> n.parentId().isEmpty())
                    .forEach(n -> content.child(n.sortOrder().value(), viewNode(view, n)));
            return node("element", header.archiId(), attributes(residue, typed, XmlSchema.VIEW_ORDER, Set.of("name")),
                    content);
        }

        private DocumentNode viewNode(View view, ViewNode node) {
            NodeResidue residue = residue(node.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(node.archiType().value()));
            typed.put("id", Optional.of(node.archiId().value()));
            typed.put(XmlSchema.TARGET_CONNECTIONS, targetConnections(node.id().value(), residue));
            typed.put("name", node.label());
            style(typed, node.style());
            typed.put("archimateElement", node.elementId().map(e -> archiIds.get(e.value()).value()));
            Content content = new Content(residue);
            content.single("bounds", bounds(node.bounds(), residue), XmlSchema.BOUNDS_FIRST);
            content.single("content", node.content().map(c -> text("content", c)), XmlSchema.CONTENT_AFTER_BOUNDS);
            ViewNodeId id = node.id();
            view.nodes().values().stream().filter(n -> n.parentId().equals(Optional.of(id)))
                    .forEach(n -> content.child(n.sortOrder().value(), viewNode(view, n)));
            view.edges().values().stream().filter(e -> e.source().equals(id))
                    .forEach(e -> content.child(e.sortOrder().value(), viewEdge(view, e)));
            return node("child", node.archiId(), attributes(residue, typed, XmlSchema.NODE_ORDER, Set.of()), content);
        }

        private DocumentNode viewEdge(View view, ViewEdge edge) {
            NodeResidue residue = residue(edge.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(edge.archiType().value()));
            typed.put("id", Optional.of(edge.archiId().value()));
            typed.put(XmlSchema.TARGET_CONNECTIONS, targetConnections(edge.id().value(), residue));
            style(typed, edge.style());
            typed.put("source", Optional.of(archiIds.get(edge.source().value()).value()));
            typed.put("target", Optional.of(archiIds.get(edge.target().value()).value()));
            typed.put("archimateRelationship", edge.relationshipId().map(r -> archiIds.get(r.value()).value()));
            Content content = new Content(residue);
            List<ResidueItem.Slot> slots = residue.slots("bendpoint");
            List<Bendpoint> points = edge.bendpoints();
            for (int i = 0; i < points.size(); i++) {
                Optional<ResidueItem.Slot> slot = i < slots.size() ? Optional.of(slots.get(i)) : Optional.empty();
                content.typed(slot.map(ResidueItem.Slot::order).orElse(lastOrder(slots, XmlSchema.BOUNDS_FIRST)),
                        bendpoint(points.get(i), slot));
            }
            ViewEdgeId id = edge.id();
            view.edges().values().stream().filter(e -> e.source().equals(id))
                    .forEach(e -> content.child(e.sortOrder().value(), viewEdge(view, e)));
            return node("sourceConnection", edge.archiId(),
                    attributes(residue, typed, XmlSchema.EDGE_ORDER, Set.of()), content);
        }

        /**
         * Множество — рёбра, входящие в объект сейчас; порядок — как был записан, новые
         * рёбра — следом, в порядке обхода. Нет входящих — нет и атрибута.
         */
        private Optional<String> targetConnections(UUID target, NodeResidue residue) {
            List<ViewEdge> edges = incoming.getOrDefault(target, List.of());
            if (edges.isEmpty()) {
                return Optional.empty();
            }
            Set<String> present = new LinkedHashSet<>();
            edges.stream().sorted(Comparator.comparingInt(e -> edgeRank.get(e.id().value())))
                    .forEach(e -> present.add(e.archiId().value()));
            List<String> ordered = new ArrayList<>();
            residue.attribute(XmlSchema.TARGET_CONNECTIONS).flatMap(ResidueAttribute::value).ifPresent(stored -> {
                for (String id : stored.trim().split("\\s+")) {
                    if (present.contains(id) && !ordered.contains(id)) {
                        ordered.add(id);
                    }
                }
            });
            present.stream().filter(id -> !ordered.contains(id)).forEach(ordered::add);
            return Optional.of(String.join(" ", ordered));
        }

        /** Порядок рёбер в файле: обход представления — узлы, затем рёбра узла, в порядке содержимого. */
        private void rankEdges(View view) {
            int[] rank = {edgeRank.size()};
            walk(view, view.nodes().values().stream().filter(n -> n.parentId().isEmpty())
                    .sorted(Comparator.comparing(ViewNode::sortOrder)).toList(), rank);
        }

        private void walk(View view, List<ViewNode> nodes, int[] rank) {
            for (ViewNode node : nodes) {
                List<Object> content = new ArrayList<>();
                view.nodes().values().stream().filter(n -> n.parentId().equals(Optional.of(node.id())))
                        .forEach(content::add);
                view.edges().values().stream().filter(e -> e.source().equals(node.id())).forEach(content::add);
                content.sort(Comparator.comparingLong(o -> o instanceof ViewNode n ? n.sortOrder().value()
                        : ((ViewEdge) o).sortOrder().value()));
                for (Object item : content) {
                    if (item instanceof ViewNode child) {
                        walk(view, List.of(child), rank);
                    } else {
                        walkEdge(view, (ViewEdge) item, rank);
                    }
                }
            }
        }

        private void walkEdge(View view, ViewEdge edge, int[] rank) {
            edgeRank.put(edge.id().value(), rank[0]++);
            view.edges().values().stream().filter(e -> e.source().equals(edge.id()))
                    .sorted(Comparator.comparing(ViewEdge::sortOrder)).forEach(e -> walkEdge(view, e, rank));
        }

        // ── Значения ────────────────────────────────────────────────

        private Optional<DocumentValue> bounds(Bounds bounds, NodeResidue residue) {
            Map<String, Integer> values = Map.of("x", bounds.x(), "y", bounds.y(), "width", bounds.width(),
                    "height", bounds.height());
            Optional<ResidueItem.Slot> slot = residue.slots("bounds").stream().findFirst();
            List<Attribute> attributes = numbers(values, XmlSchema.BOUNDS, XmlSchema.BOUNDS_DEFAULT, slot);
            if (slot.isEmpty() && attributes.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new DocumentValue("bounds", DocumentOrder.of(0), new Attributes(attributes),
                    Optional.empty()));
        }

        private static DocumentValue bendpoint(Bendpoint point, Optional<ResidueItem.Slot> slot) {
            Map<String, Integer> values = Map.of("startX", point.startX(), "startY", point.startY(),
                    "endX", point.endX(), "endY", point.endY());
            Map<String, Integer> defaults = Map.of("startX", 0, "startY", 0, "endX", 0, "endY", 0);
            return new DocumentValue("bendpoint", DocumentOrder.of(0),
                    new Attributes(numbers(values, XmlSchema.BENDPOINT, defaults, slot)), Optional.empty());
        }

        /**
         * Атрибуты числового значения: размеченные в остатке — всегда, в своём порядке;
         * прочие — только отличные от умолчания, которого Archi не пишет.
         */
        private static List<Attribute> numbers(Map<String, Integer> values, List<String> canonical,
                                               Map<String, Integer> defaults, Optional<ResidueItem.Slot> slot) {
            List<Attribute> attributes = new ArrayList<>();
            Set<String> placed = new LinkedHashSet<>();
            slot.ifPresent(s -> s.attributes().forEach(a -> {
                attributes.add(new Attribute(a.name(), String.valueOf(values.get(a.name()))));
                placed.add(a.name());
            }));
            for (String name : canonical) {
                if (!placed.contains(name) && !values.get(name).equals(defaults.get(name))) {
                    attributes.add(new Attribute(name, String.valueOf(values.get(name))));
                }
            }
            return attributes;
        }

        private static void style(Map<String, Optional<String>> typed, StyleOverride style) {
            typed.put("fillColor", style.fillColor());
            typed.put("font", style.font());
            typed.put("fontColor", style.fontColor());
            typed.put("lineColor", style.lineColor());
            typed.put("textAlignment", style.textAlignment().map(String::valueOf));
        }

        private String archiId(ConceptRef concept) {
            return archiIds.get(concept.value()).value();
        }

        /** Пустая документация — элемент без текста: так её отдаёт читатель, и так она уходит. */
        private static DocumentValue text(String tag, String text) {
            return new DocumentValue(tag, DocumentOrder.of(0), new Attributes(List.of()),
                    Optional.of(text).filter(t -> !t.isEmpty()));
        }
    }

    // ── Атрибуты ────────────────────────────────────────────────────

    /**
     * Атрибуты по остатку: метка — значение столбца (нет значения — нет атрибута),
     * буквальный — как был. Типизированные без метки дописываются в конец: значение
     * появилось после импорта. Без остатка — порядок Archi.
     *
     * @param skipWhenEmpty строковые атрибуты, которые без метки и пустыми не пишутся
     */
    private static List<Attribute> attributes(NodeResidue residue, Map<String, Optional<String>> typed,
                                              List<String> canonical, Set<String> skipWhenEmpty) {
        if (residue.empty()) {
            List<Attribute> attributes = new ArrayList<>();
            for (String name : canonical) {
                typed.getOrDefault(name, Optional.empty()).filter(v -> !v.isEmpty() || !skipWhenEmpty.contains(name))
                        .ifPresent(v -> attributes.add(new Attribute(name, v)));
            }
            return attributes;
        }
        return attributes(residue, typed, skipWhenEmpty);
    }

    private static List<Attribute> attributes(NodeResidue residue, Map<String, Optional<String>> typed,
                                              Set<String> skipWhenEmpty) {
        List<Attribute> attributes = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        for (ResidueAttribute attribute : residue.attributes()) {
            placed.add(attribute.name());
            if (typed.containsKey(attribute.name()) && (attribute.typed()
                    || attribute.name().equals(XmlSchema.TARGET_CONNECTIONS))) {
                typed.get(attribute.name()).ifPresent(v -> attributes.add(new Attribute(attribute.name(), v)));
            } else if (attribute.value().isPresent()) {
                attributes.add(new Attribute(attribute.name(), attribute.value().get()));
            }
        }
        typed.forEach((name, value) -> {
            if (!placed.contains(name)) {
                value.filter(v -> !v.isEmpty() || !skipWhenEmpty.contains(name))
                        .ifPresent(v -> attributes.add(new Attribute(name, v)));
            }
        });
        return attributes;
    }

    private static List<Attribute> newModelAttributes(Map<String, Optional<String>> typed) {
        List<Attribute> attributes = new ArrayList<>();
        XmlSchema.NEW_MODEL_NAMESPACES.forEach(ns -> attributes.add(new Attribute(ns[0], ns[1])));
        for (String name : XmlSchema.ROOT_ORDER) {
            typed.get(name).ifPresent(v -> attributes.add(new Attribute(name, v)));
        }
        return attributes;
    }

    // ── Слияние содержимого ─────────────────────────────────────────

    /**
     * Содержимое узла: дочерние строки по {@code sort_order}, записи остатка по своим
     * позициям, типизированные значения — на места своих меток. Порядок при равенстве
     * позиций — порядок добавления: стабильно, без хеш-таблиц: одна модель
     * всегда даёт один и тот же файл.
     */
    private static final class Content {

        private final NodeResidue residue;
        private final List<Entry> entries = new ArrayList<>();
        private int sequence;

        Content(NodeResidue residue) {
            this.residue = residue;
            for (ResidueItem item : residue.items()) {
                switch (item) {
                    case ResidueItem.Value value -> entries.add(new Entry(value.order(), sequence++, parent ->
                            new DocumentValue(value.tag(), DocumentOrder.of(0), literal(value.attributes()),
                                    value.text())));
                    case ResidueItem.Fragment fragment -> entries.add(new Entry(fragment.order(), sequence++,
                            parent -> new RawXmlFragment(fragment.xml(), parent, DocumentOrder.of(0))));
                    case ResidueItem.Slot slot -> {
                        // Место занимает типизированное значение — его кладут single/properties.
                    }
                }
            }
        }

        /** Одиночное значение (документация, геометрия): на место метки или в начало. */
        void single(String tag, Optional<DocumentValue> value, long fallback) {
            value.ifPresent(v -> typed(residue.slots(tag).stream().findFirst().map(ResidueItem.Slot::order)
                    .orElse(fallback), v));
        }

        /** Свойства — на свои места по очереди; лишние — вслед за последним. */
        void properties(List<PropertyEntry> properties, long fallback) {
            List<ResidueItem.Slot> slots = residue.slots("property");
            List<PropertyEntry> ordered = properties.stream().sorted(Comparator.comparing(PropertyEntry::sortOrder))
                    .toList();
            for (int i = 0; i < ordered.size(); i++) {
                Optional<ResidueItem.Slot> slot = i < slots.size() ? Optional.of(slots.get(i)) : Optional.empty();
                typed(slot.map(ResidueItem.Slot::order).orElse(lastOrder(slots, fallback)), property(ordered.get(i), slot));
            }
        }

        void typed(long order, DocumentContent value) {
            entries.add(new Entry(order, sequence++, parent -> value));
        }

        void child(long order, DocumentNode node) {
            entries.add(new Entry(order, sequence++, parent -> node));
        }

        List<DocumentContent> build(Optional<uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId> parent) {
            entries.sort(Comparator.comparingLong(Entry::order).thenComparingInt(Entry::sequence));
            List<DocumentContent> content = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                content.add(reorder(entries.get(i).build().apply(parent), i));
            }
            return content;
        }

        private static DocumentValue property(PropertyEntry property, Optional<ResidueItem.Slot> slot) {
            Map<String, String> values = Map.of("key", property.key(), "value", property.value());
            List<Attribute> attributes = new ArrayList<>();
            Set<String> placed = new LinkedHashSet<>();
            slot.ifPresent(s -> s.attributes().forEach(a -> {
                attributes.add(new Attribute(a.name(), values.get(a.name())));
                placed.add(a.name());
            }));
            for (String name : XmlSchema.PROPERTY) {
                if (!placed.contains(name) && (slot.isEmpty() || !values.get(name).isEmpty())) {
                    attributes.add(new Attribute(name, values.get(name)));
                }
            }
            return new DocumentValue("property", DocumentOrder.of(0), new Attributes(attributes), Optional.empty());
        }

        private static Attributes literal(List<ResidueAttribute> attributes) {
            return new Attributes(attributes.stream().map(a -> new Attribute(a.name(), a.value().orElseThrow())).toList());
        }

        private static DocumentContent reorder(DocumentContent item, int position) {
            DocumentOrder order = DocumentOrder.of(position);
            return switch (item) {
                case DocumentNode node -> new DocumentNode(node.tag(), order, node.attributes(), node.content());
                case DocumentValue value -> new DocumentValue(value.tag(), order, value.attributes(), value.text());
                case RawXmlFragment raw -> new RawXmlFragment(raw.xml(), raw.parentArchiId(), order);
            };
        }

        private record Entry(long order, int sequence,
                             Function<Optional<uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId>,
                                     DocumentContent> build) {
        }
    }

    private static DocumentNode node(String tag, ArchiId archiId, List<Attribute> attributes, Content content) {
        var id = uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId.of(archiId.value());
        return new DocumentNode(tag, DocumentOrder.of(0), new Attributes(attributes), content.build(Optional.of(id)));
    }

    private static long lastOrder(List<ResidueItem.Slot> slots, long fallback) {
        return slots.isEmpty() ? fallback : slots.getLast().order();
    }

    private static NodeResidue residue(Optional<RawXml> raw) {
        return raw.map(r -> NodeResidue.decode(r.value())).orElse(NodeResidue.EMPTY);
    }

}
