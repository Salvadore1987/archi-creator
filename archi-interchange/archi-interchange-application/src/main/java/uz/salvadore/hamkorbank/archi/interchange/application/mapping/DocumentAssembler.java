package uz.salvadore.hamkorbank.archi.interchange.application.mapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
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
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.ModelRows;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;

/**
 * Строки модели ({@code ModelRows}) — обратно в документ {@code .archimate} ({@code ModelDocumentAssembler}).
 *
 * <p>Столбцы накладываются на остаток и сильнее него: метка типизированного атрибута получает
 * значение из столбца, пропавшее значение убирается, новое встаёт туда, куда его поставил
 * бы Archi. Дочерние строки и записи остатка сливаются по общей разреженной нумерации.
 * {@code targetConnections} производен: множество — из рёбер, порядок — из остатка.
 */
public final class DocumentAssembler {

    /** Собирает документ {@code .archimate} из строк модели и остатка XML. */
    public ModelDocument assemble(ModelRows rows) {
        return new Run(rows).assemble();
    }

    private static final class Run {

        private final ModelRows rows;
        private final Map<UUID, String> archiIds = new HashMap<>();
        private final Map<UUID, List<ModelRows.Edge>> incoming = new HashMap<>();
        private final Map<UUID, Integer> edgeRank = new HashMap<>();
        // Дети по родителю — один проход здесь, а не перебор всей модели на каждую папку и узел.
        // Списки в порядке строк: как раньше их отдавал фильтр по всем объектам.
        private final Map<UUID, List<ModelRows.Folder>> subfolders = new HashMap<>();
        private final Map<UUID, List<ModelRows.Element>> elementsIn = new HashMap<>();
        private final Map<UUID, List<ModelRows.Relationship>> relationshipsIn = new HashMap<>();
        private final Map<UUID, List<ModelRows.View>> viewsIn = new HashMap<>();
        private final Map<UUID, List<ModelRows.Node>> nested = new HashMap<>();
        private final Map<UUID, List<ModelRows.Edge>> outgoing = new HashMap<>();

        Run(ModelRows rows) {
            this.rows = rows;
            rows.folders().stream().filter(f -> f.parentId() != null)
                    .forEach(f -> subfolders.computeIfAbsent(f.parentId(), k -> new ArrayList<>()).add(f));
            rows.elements().forEach(e -> {
                archiIds.put(e.id(), e.archiId());
                elementsIn.computeIfAbsent(e.folderId(), k -> new ArrayList<>()).add(e);
            });
            rows.relationships().forEach(r -> {
                archiIds.put(r.id(), r.archiId());
                relationshipsIn.computeIfAbsent(r.folderId(), k -> new ArrayList<>()).add(r);
            });
            for (ModelRows.View view : rows.views()) {
                viewsIn.computeIfAbsent(view.folderId(), k -> new ArrayList<>()).add(view);
                view.nodes().forEach(n -> {
                    archiIds.put(n.id(), n.archiId());
                    if (n.parentId() != null) {
                        nested.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n);
                    }
                });
                view.edges().forEach(e -> {
                    archiIds.put(e.id(), e.archiId());
                    incoming.computeIfAbsent(e.targetId(), k -> new ArrayList<>()).add(e);
                    outgoing.computeIfAbsent(e.sourceId(), k -> new ArrayList<>()).add(e);
                });
                rankEdges(view);
            }
        }

        ModelDocument assemble() {
            ModelRows.Header header = rows.header();
            NodeResidue residue = residue(header.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put("name", Optional.of(header.name()));
            typed.put("id", Optional.of(header.archiId()));
            typed.put("version", Optional.of(header.archiVersion()));
            List<Attribute> attributes = residue.empty() ? newModelAttributes(typed)
                    : attributes(residue, typed, Set.of("name"));
            Content content = new Content(residue);
            // У корня Archi пишет описание и свойства после папок, а не перед ними.
            content.single("purpose", textOrNull("purpose", header.documentation()),
                    XmlSchema.AFTER_ALL - 1);
            content.properties(header.properties(), XmlSchema.AFTER_ALL);
            rows.folders().stream().filter(f -> f.parentId() == null)
                    .forEach(f -> content.child(f.sortOrder(), folder(f)));
            return new ModelDocument(new Attributes(attributes), content.build(null));
        }

        // ── Дерево модели ───────────────────────────────────────────

        private DocumentNode folder(ModelRows.Folder folder) {
            NodeResidue residue = residue(folder.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put("name", Optional.of(folder.name()));
            typed.put("id", Optional.of(folder.archiId()));
            typed.put("type", Optional.ofNullable(folder.type()));
            Content content = new Content(residue);
            UUID id = folder.id();
            subfolders.getOrDefault(id, List.of()).forEach(f -> content.child(f.sortOrder(), folder(f)));
            elementsIn.getOrDefault(id, List.of()).forEach(e -> content.child(e.sortOrder(), element(e)));
            relationshipsIn.getOrDefault(id, List.of())
                    .forEach(r -> content.child(r.sortOrder(), relationship(r)));
            viewsIn.getOrDefault(id, List.of()).forEach(v -> content.child(v.sortOrder(), view(v)));
            return node("folder", folder.archiId(), attributes(residue, typed, XmlSchema.FOLDER_ORDER, Set.of()),
                    content);
        }

        private DocumentNode element(ModelRows.Element element) {
            NodeResidue residue = residue(element.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(element.archiType()));
            typed.put("name", Optional.of(element.name()));
            typed.put("id", Optional.of(element.archiId()));
            Content content = new Content(residue);
            content.single("documentation", textOrNull("documentation", element.documentation()),
                    XmlSchema.DOCUMENTATION_FIRST);
            content.properties(element.properties(), XmlSchema.PROPERTIES_FIRST);
            return node("element", element.archiId(),
                    attributes(residue, typed, XmlSchema.ELEMENT_ORDER, Set.of("name")), content);
        }

        private DocumentNode relationship(ModelRows.Relationship relationship) {
            NodeResidue residue = residue(relationship.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(relationship.archiType()));
            typed.put("name", Optional.ofNullable(relationship.name()));
            typed.put("id", Optional.of(relationship.archiId()));
            typed.put("source", Optional.of(archiIds.get(relationship.sourceId())));
            typed.put("target", Optional.of(archiIds.get(relationship.targetId())));
            typed.put("accessType", Optional.ofNullable(relationship.accessType()));
            typed.put("directed", Optional.ofNullable(relationship.directed()).map(String::valueOf));
            Content content = new Content(residue);
            content.single("documentation", textOrNull("documentation", relationship.documentation()),
                    XmlSchema.DOCUMENTATION_FIRST);
            content.properties(relationship.properties(), XmlSchema.PROPERTIES_FIRST);
            return node("element", relationship.archiId(),
                    attributes(residue, typed, XmlSchema.RELATIONSHIP_ORDER, Set.of()), content);
        }

        // ── Представления ───────────────────────────────────────────

        private DocumentNode view(ModelRows.View view) {
            NodeResidue residue = residue(view.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(view.archiType()));
            typed.put("name", Optional.of(view.name()));
            typed.put("id", Optional.of(view.archiId()));
            typed.put("viewpoint", Optional.ofNullable(view.viewpoint()));
            Content content = new Content(residue);
            content.single("documentation", textOrNull("documentation", view.documentation()),
                    XmlSchema.DOCUMENTATION_FIRST);
            content.properties(view.properties(), XmlSchema.PROPERTIES_FIRST);
            view.nodes().stream().filter(n -> n.parentId() == null)
                    .forEach(n -> content.child(n.sortOrder(), viewNode(n)));
            return node("element", view.archiId(), attributes(residue, typed, XmlSchema.VIEW_ORDER, Set.of("name")),
                    content);
        }

        private DocumentNode viewNode(ModelRows.Node node) {
            NodeResidue residue = residue(node.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(node.archiType()));
            typed.put("id", Optional.of(node.archiId()));
            typed.put(XmlSchema.TARGET_CONNECTIONS, targetConnections(node.id(), residue));
            typed.put("name", Optional.ofNullable(node.label()));
            style(typed, node.style());
            typed.put("archimateElement", Optional.ofNullable(node.elementId()).map(archiIds::get));
            Content content = new Content(residue);
            content.single("bounds", bounds(node.bounds(), residue), XmlSchema.BOUNDS_FIRST);
            content.single("content", textOrNull("content", node.content()),
                    XmlSchema.CONTENT_AFTER_BOUNDS);
            UUID id = node.id();
            nested.getOrDefault(id, List.of()).forEach(n -> content.child(n.sortOrder(), viewNode(n)));
            outgoing.getOrDefault(id, List.of()).forEach(e -> content.child(e.sortOrder(), viewEdge(e)));
            return node("child", node.archiId(), attributes(residue, typed, XmlSchema.NODE_ORDER, Set.of()), content);
        }

        private DocumentNode viewEdge(ModelRows.Edge edge) {
            NodeResidue residue = residue(edge.rawXml());
            Map<String, Optional<String>> typed = new LinkedHashMap<>();
            typed.put(XmlSchema.XSI_TYPE, Optional.of(edge.archiType()));
            typed.put("id", Optional.of(edge.archiId()));
            typed.put(XmlSchema.TARGET_CONNECTIONS, targetConnections(edge.id(), residue));
            style(typed, edge.style());
            typed.put("source", Optional.of(archiIds.get(edge.sourceId())));
            typed.put("target", Optional.of(archiIds.get(edge.targetId())));
            typed.put("archimateRelationship", Optional.ofNullable(edge.relationshipId()).map(archiIds::get));
            Content content = new Content(residue);
            List<ResidueItem.Slot> slots = residue.slots("bendpoint");
            List<BendpointValue> points = edge.bendpoints();
            for (int i = 0; i < points.size(); i++) {
                ResidueItem.Slot slot = i < slots.size() ? slots.get(i) : null;
                content.typed(slot != null ? slot.order() : lastOrder(slots, XmlSchema.BOUNDS_FIRST),
                        bendpoint(points.get(i), slot));
            }
            outgoing.getOrDefault(edge.id(), List.of())
                    .forEach(e -> content.child(e.sortOrder(), viewEdge(e)));
            return node("sourceConnection", edge.archiId(),
                    attributes(residue, typed, XmlSchema.EDGE_ORDER, Set.of()), content);
        }

        /**
         * Множество — рёбра, входящие в объект сейчас; порядок — как был записан, новые
         * рёбра — следом, в порядке обхода. Нет входящих — нет и атрибута.
         */
        private Optional<String> targetConnections(UUID target, NodeResidue residue) {
            List<ModelRows.Edge> edges = incoming.getOrDefault(target, List.of());
            if (edges.isEmpty()) {
                return Optional.empty();
            }
            Set<String> present = new LinkedHashSet<>();
            edges.stream().sorted(Comparator.comparingInt(e -> edgeRank.get(e.id())))
                    .forEach(e -> present.add(e.archiId()));
            Set<String> ordered = new LinkedHashSet<>();
            residue.attribute(XmlSchema.TARGET_CONNECTIONS).map(ResidueAttribute::value).ifPresent(stored -> {
                for (String id : stored.trim().split("\\s+")) {
                    if (present.contains(id)) {
                        ordered.add(id);
                    }
                }
            });
            ordered.addAll(present);
            return Optional.of(String.join(" ", ordered));
        }

        /** Порядок рёбер в файле: обход представления — узлы, затем рёбра узла, в порядке содержимого. */
        private void rankEdges(ModelRows.View view) {
            int[] rank = {edgeRank.size()};
            walk(view.nodes().stream().filter(n -> n.parentId() == null)
                    .sorted(Comparator.comparingLong(ModelRows.Node::sortOrder)).toList(), rank);
        }

        private void walk(List<ModelRows.Node> nodes, int[] rank) {
            for (ModelRows.Node node : nodes) {
                List<Object> content = new ArrayList<>(nested.getOrDefault(node.id(), List.of()));
                content.addAll(outgoing.getOrDefault(node.id(), List.of()));
                content.sort(Comparator.comparingLong(o -> o instanceof ModelRows.Node n ? n.sortOrder()
                        : ((ModelRows.Edge) o).sortOrder()));
                for (Object item : content) {
                    if (item instanceof ModelRows.Node child) {
                        walk(List.of(child), rank);
                    } else {
                        walkEdge((ModelRows.Edge) item, rank);
                    }
                }
            }
        }

        private void walkEdge(ModelRows.Edge edge, int[] rank) {
            edgeRank.put(edge.id(), rank[0]++);
            outgoing.getOrDefault(edge.id(), List.of()).stream()
                    .sorted(Comparator.comparingLong(ModelRows.Edge::sortOrder)).forEach(e -> walkEdge(e, rank));
        }

        // ── Значения ────────────────────────────────────────────────

        /** Геометрия узла; {@code null} — без метки в остатке и вся по умолчанию: Archi её не пишет. */
        private DocumentValue bounds(ModelRows.Box bounds, NodeResidue residue) {
            Map<String, Integer> values = Map.of("x", bounds.x(), "y", bounds.y(), "width", bounds.width(),
                    "height", bounds.height());
            ResidueItem.Slot slot = residue.slots("bounds").stream().findFirst().orElse(null);
            List<Attribute> attributes = numbers(values, XmlSchema.BOUNDS, XmlSchema.BOUNDS_DEFAULT, slot);
            if (slot == null && attributes.isEmpty()) {
                return null;
            }
            return new DocumentValue("bounds", DocumentOrder.of(0), new Attributes(attributes), null);
        }

        /** Точка перегиба; {@code slot} — её метка в остатке, {@code null} — метки нет. */
        private static DocumentValue bendpoint(BendpointValue point, ResidueItem.Slot slot) {
            Map<String, Integer> values = Map.of("startX", point.startX(), "startY", point.startY(),
                    "endX", point.endX(), "endY", point.endY());
            Map<String, Integer> defaults = Map.of("startX", 0, "startY", 0, "endX", 0, "endY", 0);
            return new DocumentValue("bendpoint", DocumentOrder.of(0),
                    new Attributes(numbers(values, XmlSchema.BENDPOINT, defaults, slot)), null);
        }

        /**
         * Атрибуты числового значения: размеченные в остатке — всегда, в своём порядке;
         * прочие — только отличные от умолчания, которого Archi не пишет. Метки нет — {@code slot} {@code null}.
         */
        private static List<Attribute> numbers(Map<String, Integer> values, List<String> canonical,
                                               Map<String, Integer> defaults, ResidueItem.Slot slot) {
            List<Attribute> attributes = new ArrayList<>();
            Set<String> placed = new LinkedHashSet<>();
            if (slot != null) {
                slot.attributes().forEach(a -> {
                    attributes.add(new Attribute(a.name(), String.valueOf(values.get(a.name()))));
                    placed.add(a.name());
                });
            }
            for (String name : canonical) {
                if (!placed.contains(name) && !values.get(name).equals(defaults.get(name))) {
                    attributes.add(new Attribute(name, String.valueOf(values.get(name))));
                }
            }
            return attributes;
        }

        private static void style(Map<String, Optional<String>> typed, StyleValue style) {
            typed.put("fillColor", Optional.ofNullable(style.fillColor()));
            typed.put("font", Optional.ofNullable(style.font()));
            typed.put("fontColor", Optional.ofNullable(style.fontColor()));
            typed.put("lineColor", Optional.ofNullable(style.lineColor()));
            typed.put("textAlignment", Optional.ofNullable(style.textAlignment()).map(String::valueOf));
        }

        /**
         * Пустая документация — элемент без текста: так её отдаёт читатель, и так она уходит.
         * Текста нет вовсе ({@code null}) — нет и значения.
         */
        private static DocumentValue textOrNull(String tag, String text) {
            if (text == null) {
                return null;
            }
            return new DocumentValue(tag, DocumentOrder.of(0), new Attributes(List.of()), text.isEmpty() ? null : text);
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
            } else if (attribute.value() != null) {
                attributes.add(new Attribute(attribute.name(), attribute.value()));
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

        /** Одиночное значение (документация, геометрия): на место метки или в начало; {@code null} — нет значения. */
        void single(String tag, DocumentValue value, long fallback) {
            if (value != null) {
                typed(residue.slots(tag).stream().findFirst().map(ResidueItem.Slot::order).orElse(fallback), value);
            }
        }

        /** Свойства — на свои места по очереди; лишние — вслед за последним. */
        void properties(List<ModelRows.Property> properties, long fallback) {
            List<ResidueItem.Slot> slots = residue.slots("property");
            List<ModelRows.Property> ordered = properties.stream()
                    .sorted(Comparator.comparingLong(ModelRows.Property::sortOrder)).toList();
            for (int i = 0; i < ordered.size(); i++) {
                ResidueItem.Slot slot = i < slots.size() ? slots.get(i) : null;
                typed(slot != null ? slot.order() : lastOrder(slots, fallback), property(ordered.get(i), slot));
            }
        }

        void typed(long order, DocumentContent value) {
            entries.add(new Entry(order, sequence++, parent -> value));
        }

        void child(long order, DocumentNode node) {
            entries.add(new Entry(order, sequence++, parent -> node));
        }

        /** Содержимое узла {@code parent}; {@code null} — корня модели. */
        List<DocumentContent> build(ArchiId parent) {
            entries.sort(Comparator.comparingLong(Entry::order).thenComparingInt(Entry::sequence));
            List<DocumentContent> content = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                content.add(reorder(entries.get(i).build().apply(parent), i));
            }
            return content;
        }

        /** Свойство на месте метки {@code slot}; {@code null} — метки нет. */
        private static DocumentValue property(ModelRows.Property property, ResidueItem.Slot slot) {
            Map<String, String> values = Map.of("key", property.key(), "value", property.value());
            List<Attribute> attributes = new ArrayList<>();
            Set<String> placed = new LinkedHashSet<>();
            if (slot != null) {
                slot.attributes().forEach(a -> {
                    attributes.add(new Attribute(a.name(), values.get(a.name())));
                    placed.add(a.name());
                });
            }
            for (String name : XmlSchema.PROPERTY) {
                if (!placed.contains(name) && (slot == null || !values.get(name).isEmpty())) {
                    attributes.add(new Attribute(name, values.get(name)));
                }
            }
            return new DocumentValue("property", DocumentOrder.of(0), new Attributes(attributes), null);
        }

        private static Attributes literal(List<ResidueAttribute> attributes) {
            return new Attributes(attributes.stream()
                    .map(a -> new Attribute(a.name(), Objects.requireNonNull(a.value()))).toList());
        }

        private static DocumentContent reorder(DocumentContent item, int position) {
            DocumentOrder order = DocumentOrder.of(position);
            return switch (item) {
                case DocumentNode node -> new DocumentNode(node.tag(), order, node.attributes(), node.content());
                case DocumentValue value -> new DocumentValue(value.tag(), order, value.attributes(), value.text());
                case RawXmlFragment raw -> new RawXmlFragment(raw.xml(), raw.parentArchiId(), order);
            };
        }

        private record Entry(long order, int sequence, Function<ArchiId, DocumentContent> build) {
        }
    }

    private static DocumentNode node(String tag, String archiId, List<Attribute> attributes, Content content) {
        return new DocumentNode(tag, DocumentOrder.of(0), new Attributes(attributes),
                content.build(ArchiId.of(archiId)));
    }

    private static long lastOrder(List<ResidueItem.Slot> slots, long fallback) {
        return slots.isEmpty() ? fallback : slots.getLast().order();
    }

    /** Остаток узла; {@code null} — остатка нет. */
    private static NodeResidue residue(String raw) {
        return raw == null ? NodeResidue.EMPTY : NodeResidue.decode(raw);
    }

}
