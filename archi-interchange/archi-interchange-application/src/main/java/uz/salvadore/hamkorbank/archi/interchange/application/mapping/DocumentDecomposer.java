package uz.salvadore.hamkorbank.archi.interchange.application.mapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.residue.NodeResidue;
import uz.salvadore.hamkorbank.archi.interchange.domain.residue.ResidueAttribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.residue.ResidueItem;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ConceptRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bendpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.StyleOverride;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Документ {@code .archimate} — в агрегаты modeling (UC-IXC-001, п. 5; ADR-0017).
 *
 * <p>Каждый узел с идентификатором становится строкой: типизированное — в поля, остальное —
 * в остаток с позициями. Позиция любого содержимого — номер в родителе, разреженный
 * шагом 1000, общий для дочерних строк и для записей остатка: так сборщик вернёт всё
 * в исходном порядке, а вставка между соседями их не перенумерует (INV-MDL-005).
 *
 * <p>Идентификаторы Archi сохраняются буквально (INV-IXC-001), внутренние ключи — новые.
 */
public final class DocumentDecomposer {

    private static final ArchiTypeRegistry REGISTRY = ArchiTypeRegistry.archimate32();

    private final Supplier<UUID> uuids;
    private final Supplier<ArchiId> archiIds;

    public DocumentDecomposer(Supplier<UUID> uuids, Supplier<ArchiId> archiIds) {
        this.uuids = uuids;
        this.archiIds = archiIds;
    }

    /**
     * Чья модель получится: ключ, пространство, автор и время. Импорт даёт новые,
     * откат к версии — те же, что у модели, чьё содержимое заменяется.
     */
    public record Identity(ModelId modelId, WorkspaceId workspaceId, String createdBy, Instant createdAt,
                           Instant now, long version) {
    }

    public ModelContent decompose(ModelDocument document, Identity identity) {
        return new Run(document, identity).decompose();
    }

    /** Одна раскладка: ключи узлов и накопленные строки. */
    private final class Run {

        private final ModelDocument document;
        private final Identity identity;
        private final Map<String, UUID> keys = new HashMap<>();
        private final Map<String, DocumentNode> byArchiId = new HashMap<>();

        private final List<ModelFolder> folders = new ArrayList<>();
        private final List<Element> elements = new ArrayList<>();
        private final List<Relationship> relationships = new ArrayList<>();
        private final List<ViewParts> views = new ArrayList<>();

        Run(ModelDocument document, Identity identity) {
            this.document = document;
            this.identity = identity;
            for (DocumentNode node : document.allNodes()) {
                keys.put(node.archiId().value(), uuids.get());
                byArchiId.put(node.archiId().value(), node);
            }
        }

        ModelContent decompose() {
            Residue root = new Residue(document.attributes().list(), XmlSchema.ROOT_TYPED);
            String[] documentation = {null};
            List<PropertyEntry> properties = new ArrayList<>();
            List<DocumentContent> content = document.content();
            for (int i = 0; i < content.size(); i++) {
                long order = SortOrder.ofPosition(i).value();
                switch (content.get(i)) {
                    case DocumentNode node when node.tag().equals("folder") -> folder(node, Optional.empty(), order);
                    case DocumentNode node -> throw unexpected(node, "в корне модели");
                    case DocumentValue value when documentation[0] == null && isDocumentation(value, "purpose") -> {
                        documentation[0] = value.text().orElse("");
                        root.slot(order, value);
                    }
                    case DocumentValue value when isProperty(value) -> {
                        properties.add(property(value, order));
                        root.slot(order, value);
                    }
                    case DocumentValue value -> root.value(order, value);
                    case RawXmlFragment raw -> root.fragment(order, raw);
                }
            }
            String version = document.attributes().get("version").filter(ModelHeader::validArchiVersion)
                    .orElse(ModelHeader.DEFAULT_ARCHI_VERSION);
            ModelHeader header = new ModelHeader(identity.modelId(), identity.workspaceId(),
                    ArchiId.of(document.archiId().value()), document.name(), Optional.ofNullable(documentation[0]), version,
                    ModelStatus.ACTIVE, properties, root.encode(), identity.createdBy(), identity.createdAt(),
                    identity.now(), identity.version());
            ArchitectureModel model = ArchitectureModel.imported(header, folders, elements, relationships,
                    views.stream().map(ViewParts::ref).toList(), () -> FolderId.of(uuids.get()), archiIds);
            List<View> built = views.stream()
                    .map(v -> View.imported(v.header(), v.nodes(), v.edges(), model)).toList();
            return new ModelContent(model, built);
        }

        // ── Папки и их содержимое ───────────────────────────────────

        private void folder(DocumentNode node, Optional<FolderId> parent, long order) {
            FolderId id = FolderId.of(key(node));
            Optional<FolderType> type = parent.isPresent() ? Optional.empty()
                    : node.attribute("type").flatMap(FolderType::fromFileValue);
            if (parent.isEmpty() && type.isEmpty()) {
                throw new IllegalArgumentException("корневая папка " + node.archiId() + " без типа Archi");
            }
            Residue residue = new Residue(node.attributes().list(),
                    type.isPresent() ? XmlSchema.FOLDER_TYPED : java.util.Set.of("name", "id"));
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long childOrder = SortOrder.ofPosition(i).value();
                switch (content.get(i)) {
                    case DocumentNode child when child.tag().equals("folder") -> folder(child, Optional.of(id), childOrder);
                    case DocumentNode child when child.tag().equals("element") -> modelObject(child, id, childOrder);
                    case DocumentNode child -> throw unexpected(child, "в папке");
                    case DocumentValue value -> residue.value(childOrder, value);
                    case RawXmlFragment raw -> residue.fragment(childOrder, raw);
                }
            }
            folders.add(new ModelFolder(id, parent, archiId(node), node.attribute("name").orElse(""), type,
                    SortOrder.of(order), residue.encode()));
        }

        private void modelObject(DocumentNode node, FolderId folder, long order) {
            String xsiType = node.archiType().orElseThrow(() -> unexpected(node, "без xsi:type"));
            if (xsiType.endsWith("Relationship")) {
                relationship(node, folder, order);
            } else if (xsiType.endsWith("Model")) {
                view(node, folder, order);
            } else {
                element(node, folder, order);
            }
        }

        private void element(DocumentNode node, FolderId folder, long order) {
            ArchiType type = ArchiType.of(node.archiType().orElseThrow());
            Residue residue = new Residue(node.attributes().list(), XmlSchema.ELEMENT_TYPED);
            Described described = describedContent(node, residue);
            boolean supported = REGISTRY.find(type)
                    .filter(c -> c.kind() == ConceptKind.ELEMENT || c.kind() == ConceptKind.JUNCTION)
                    .map(c -> c.supported()).orElse(false);
            elements.add(new Element(ElementId.of(key(node)), folder, archiId(node), type,
                    node.attribute("name").orElse(""), described.documentation(), described.properties(),
                    SortOrder.of(order), supported, residue.encode()));
        }

        private void relationship(DocumentNode node, FolderId folder, long order) {
            ArchiType type = ArchiType.of(node.archiType().orElseThrow());
            Optional<AccessType> accessType = node.attribute("accessType").flatMap(AccessType::fromFileValue);
            Optional<Boolean> directed = node.attribute("directed")
                    .filter(v -> v.equals("true") || v.equals("false")).map(Boolean::parseBoolean);
            Residue residue = new Residue(node.attributes().list(), XmlSchema.RELATIONSHIP_TYPED) {
                @Override
                boolean typed(String name, String value) {
                    return switch (name) {
                        case "accessType" -> accessType.isPresent();
                        case "directed" -> directed.isPresent();
                        default -> super.typed(name, value);
                    };
                }
            };
            Described described = describedContent(node, residue);
            boolean supported = RelationshipType.fromArchiType(type).isPresent();
            relationships.add(new Relationship(RelationshipId.of(key(node)), folder, archiId(node), type,
                    concept(node, "source"), concept(node, "target"), node.attribute("name"),
                    described.documentation(), accessType, directed, described.properties(), SortOrder.of(order),
                    supported, residue.encode()));
        }

        private ConceptRef concept(DocumentNode node, String attribute) {
            String target = node.attribute(attribute).orElseThrow(() -> unexpected(node, "без " + attribute));
            DocumentNode end = byArchiId.get(target);
            if (end == null) {
                throw new IllegalArgumentException("связь " + node.archiId() + " ссылается на " + target + " вне модели");
            }
            UUID id = keys.get(target);
            return end.archiType().filter(t -> t.endsWith("Relationship")).isPresent()
                    ? RelationshipId.of(id) : ElementId.of(id);
        }

        /** Документация и свойства элемента или связи; прочее — в остаток. */
        private Described describedContent(DocumentNode node, Residue residue) {
            String[] documentation = {null};
            List<PropertyEntry> properties = new ArrayList<>();
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long order = SortOrder.ofPosition(i).value();
                switch (content.get(i)) {
                    case DocumentValue value when documentation[0] == null && isDocumentation(value, "documentation") -> {
                        documentation[0] = value.text().orElse("");
                        residue.slot(order, value);
                    }
                    case DocumentValue value when isProperty(value) -> {
                        properties.add(property(value, order));
                        residue.slot(order, value);
                    }
                    case DocumentValue value -> residue.value(order, value);
                    case RawXmlFragment raw -> residue.fragment(order, raw);
                    case DocumentNode child -> throw unexpected(child, "внутри " + node.archiId());
                }
            }
            return new Described(Optional.ofNullable(documentation[0]), properties);
        }

        // ── Представления ───────────────────────────────────────────

        private void view(DocumentNode node, FolderId folder, long order) {
            ViewId id = ViewId.of(key(node));
            ViewParts parts = new ViewParts(id);
            Residue residue = new Residue(node.attributes().list(), XmlSchema.VIEW_TYPED);
            String[] documentation = {null};
            List<PropertyEntry> properties = new ArrayList<>();
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long childOrder = SortOrder.ofPosition(i).value();
                switch (content.get(i)) {
                    case DocumentNode child when child.tag().equals("child") ->
                            viewNode(child, Optional.empty(), childOrder, parts);
                    case DocumentNode child -> throw unexpected(child, "в представлении");
                    case DocumentValue value when documentation[0] == null && isDocumentation(value, "documentation") -> {
                        documentation[0] = value.text().orElse("");
                        residue.slot(childOrder, value);
                    }
                    case DocumentValue value when isProperty(value) -> {
                        properties.add(property(value, childOrder));
                        residue.slot(childOrder, value);
                    }
                    case DocumentValue value -> residue.value(childOrder, value);
                    case RawXmlFragment raw -> residue.fragment(childOrder, raw);
                }
            }
            DiagramType type = DiagramType.of(node.archiType().orElseThrow());
            ViewHeader header = new ViewHeader(id, identity.modelId(), folder, archiId(node), type,
                    node.attribute("name").orElse(""), Optional.ofNullable(documentation[0]), node.attribute("viewpoint"),
                    properties,
                    SortOrder.of(order), residue.encode(), 0);
            parts.header = header;
            parts.ref = new ViewRef(id, folder, header.archiId(), type, header.name(), header.sortOrder());
            views.add(parts);
        }

        private void viewNode(DocumentNode node, Optional<ViewNodeId> parent, long order, ViewParts view) {
            ViewNodeId id = ViewNodeId.of(key(node));
            Optional<ElementId> element = node.attribute("archimateElement")
                    .filter(ref -> byArchiId.containsKey(ref) && isElement(byArchiId.get(ref)))
                    .map(ref -> ElementId.of(keys.get(ref)));
            StyleParse style = new StyleParse(node);
            Residue residue = new Residue(node.attributes().list(), XmlSchema.NODE_TYPED) {
                @Override
                boolean typed(String name, String value) {
                    if (name.equals("archimateElement")) {
                        return element.isPresent();
                    }
                    return XmlSchema.STYLE.contains(name) ? style.typed(name) : super.typed(name, value);
                }
            };
            Bounds[] bounds = {null};
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long childOrder = SortOrder.ofPosition(i).value();
                switch (content.get(i)) {
                    case DocumentNode child when child.tag().equals("child") ->
                            viewNode(child, Optional.of(id), childOrder, view);
                    case DocumentNode child when child.tag().equals("sourceConnection") ->
                            viewEdge(child, childOrder, view);
                    case DocumentNode child -> throw unexpected(child, "в узле представления");
                    case DocumentValue value when bounds[0] == null && parseBounds(value).isPresent() -> {
                        bounds[0] = parseBounds(value).get();
                        residue.slot(childOrder, value);
                    }
                    case DocumentValue value -> residue.value(childOrder, value);
                    case RawXmlFragment raw -> residue.fragment(childOrder, raw);
                }
            }
            view.nodes.add(new ViewNode(id, parent, archiId(node), DiagramType.of(node.archiType().orElseThrow()),
                    element, Optional.ofNullable(bounds[0])
                            .orElse(new Bounds(0, 0, Bounds.DEFAULT_SIZE, Bounds.DEFAULT_SIZE)),
                    style.style(), SortOrder.of(order), residue.encode()));
        }

        private void viewEdge(DocumentNode node, long order, ViewParts view) {
            ViewEdgeId id = ViewEdgeId.of(key(node));
            Optional<RelationshipId> relationship = node.attribute("archimateRelationship")
                    .filter(ref -> byArchiId.containsKey(ref)
                            && byArchiId.get(ref).archiType().filter(t -> t.endsWith("Relationship")).isPresent())
                    .map(ref -> RelationshipId.of(keys.get(ref)));
            StyleParse style = new StyleParse(node);
            Residue residue = new Residue(node.attributes().list(), XmlSchema.EDGE_TYPED) {
                @Override
                boolean typed(String name, String value) {
                    if (name.equals("archimateRelationship")) {
                        return relationship.isPresent();
                    }
                    return XmlSchema.STYLE.contains(name) ? style.typed(name) : super.typed(name, value);
                }
            };
            List<Bendpoint> bendpoints = new ArrayList<>();
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long childOrder = SortOrder.ofPosition(i).value();
                switch (content.get(i)) {
                    case DocumentNode child when child.tag().equals("sourceConnection") ->
                            viewEdge(child, childOrder, view);
                    case DocumentNode child -> throw unexpected(child, "в ребре представления");
                    case DocumentValue value when parseBendpoint(value).isPresent() -> {
                        bendpoints.add(parseBendpoint(value).get());
                        residue.slot(childOrder, value);
                    }
                    case DocumentValue value -> residue.value(childOrder, value);
                    case RawXmlFragment raw -> residue.fragment(childOrder, raw);
                }
            }
            view.edges.add(new ViewEdge(id, archiId(node), DiagramType.of(node.archiType().orElseThrow()),
                    relationship, endpoint(node, "source"), endpoint(node, "target"), bendpoints, style.style(),
                    SortOrder.of(order), residue.encode()));
        }

        private ViewEndpoint endpoint(DocumentNode edge, String attribute) {
            String ref = edge.attribute(attribute).orElseThrow(() -> unexpected(edge, "без " + attribute));
            DocumentNode end = byArchiId.get(ref);
            if (end == null) {
                throw new IllegalArgumentException("ребро " + edge.archiId() + " ссылается на " + ref + " вне модели");
            }
            UUID id = keys.get(ref);
            return end.tag().equals("sourceConnection") ? ViewEdgeId.of(id) : ViewNodeId.of(id);
        }

        // ── Мелочи ──────────────────────────────────────────────────

        private UUID key(DocumentNode node) {
            return keys.get(node.archiId().value());
        }

        private boolean isElement(DocumentNode node) {
            return node.tag().equals("element") && node.archiType()
                    .filter(t -> !t.endsWith("Relationship") && !t.endsWith("Model")).isPresent();
        }
    }

    /** Части представления, собранные до того, как модель готова проверить ссылки. */
    private static final class ViewParts {
        final ViewId id;
        ViewHeader header;
        ViewRef ref;
        final List<ViewNode> nodes = new ArrayList<>();
        final List<ViewEdge> edges = new ArrayList<>();

        ViewParts(ViewId id) {
            this.id = id;
        }

        ViewHeader header() {
            return header;
        }

        ViewRef ref() {
            return ref;
        }

        List<ViewNode> nodes() {
            return nodes;
        }

        List<ViewEdge> edges() {
            return edges;
        }
    }

    private record Described(Optional<String> documentation, List<PropertyEntry> properties) {
    }

    /**
     * Остаток одного узла: атрибуты по порядку с метками типизированных и содержимое
     * без идентификатора с позициями.
     */
    private static class Residue {

        private final List<ResidueAttribute> attributes = new ArrayList<>();
        private final List<ResidueItem> items = new ArrayList<>();
        private final java.util.Set<String> typedNames;

        Residue(List<Attribute> source, java.util.Set<String> typedNames) {
            this.typedNames = typedNames;
            for (Attribute attribute : source) {
                attributes.add(typed(attribute.name(), attribute.value())
                        ? ResidueAttribute.placeholder(attribute.name())
                        : ResidueAttribute.literal(attribute.name(), attribute.value()));
            }
        }

        boolean typed(String name, String value) {
            return typedNames.contains(name);
        }

        void slot(long order, DocumentValue value) {
            items.add(new ResidueItem.Slot(order, value.tag(), value.attributes().list().stream()
                    .map(a -> ResidueAttribute.placeholder(a.name())).toList()));
        }

        void value(long order, DocumentValue value) {
            items.add(new ResidueItem.Value(order, value.tag(), value.attributes().list().stream()
                    .map(a -> ResidueAttribute.literal(a.name(), a.value())).toList(), value.text()));
        }

        void fragment(long order, RawXmlFragment fragment) {
            items.add(new ResidueItem.Fragment(order, fragment.xml()));
        }

        /** Остаток есть у каждой импортированной строки: порядок атрибутов файла — тоже его часть. */
        Optional<RawXml> encode() {
            return Optional.of(new RawXml(new NodeResidue(attributes, items).encode()));
        }

    }

    /** Стиль узла или ребра: типизируется только то, что проходит проверку домена. */
    private static final class StyleParse {

        private final Optional<String> fillColor;
        private final Optional<String> font;
        private final Optional<String> fontColor;
        private final Optional<String> lineColor;
        private final Optional<Integer> textAlignment;

        StyleParse(DocumentNode node) {
            fillColor = node.attribute("fillColor").filter(StyleOverride::validColor);
            font = node.attribute("font");
            fontColor = node.attribute("fontColor").filter(StyleOverride::validColor);
            lineColor = node.attribute("lineColor").filter(StyleOverride::validColor);
            textAlignment = node.attribute("textAlignment").flatMap(StyleParse::integer);
        }

        boolean typed(String name) {
            return switch (name) {
                case "fillColor" -> fillColor.isPresent();
                case "font" -> font.isPresent();
                case "fontColor" -> fontColor.isPresent();
                case "lineColor" -> lineColor.isPresent();
                case "textAlignment" -> textAlignment.isPresent();
                default -> false;
            };
        }

        StyleOverride style() {
            return new StyleOverride(fillColor, font, fontColor, lineColor, textAlignment);
        }

        static Optional<Integer> integer(String value) {
            try {
                return Optional.of(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }

    // ── Распознавание типизируемых значений ─────────────────────────

    private static boolean isDocumentation(DocumentValue value, String tag) {
        return value.tag().equals(tag) && value.attributes().list().isEmpty();
    }

    private static boolean isProperty(DocumentValue value) {
        return value.tag().equals("property") && value.text().isEmpty()
                && value.attributes().list().stream().allMatch(a -> XmlSchema.PROPERTY.contains(a.name()))
                && value.attribute("key").map(k -> k.length() <= PropertyEntry.KEY_MAX).orElse(true);
    }

    private static PropertyEntry property(DocumentValue value, long order) {
        return new PropertyEntry(value.attribute("key").orElse(""), value.attribute("value").orElse(""),
                SortOrder.of(order));
    }

    static Optional<Bounds> parseBounds(DocumentValue value) {
        if (!value.tag().equals("bounds") || value.text().isPresent()
                || !value.attributes().list().stream().allMatch(a -> XmlSchema.BOUNDS.contains(a.name()))) {
            return Optional.empty();
        }
        Map<String, Integer> numbers = new HashMap<>(XmlSchema.BOUNDS_DEFAULT);
        for (Attribute attribute : value.attributes()) {
            Optional<Integer> number = StyleParse.integer(attribute.value());
            if (number.isEmpty()) {
                return Optional.empty();
            }
            numbers.put(attribute.name(), number.get());
        }
        if (!Bounds.validSize(numbers.get("width")) || !Bounds.validSize(numbers.get("height"))) {
            return Optional.empty();
        }
        return Optional.of(new Bounds(numbers.get("x"), numbers.get("y"), numbers.get("width"), numbers.get("height")));
    }

    static Optional<Bendpoint> parseBendpoint(DocumentValue value) {
        if (!value.tag().equals("bendpoint") || value.text().isPresent()
                || !value.attributes().list().stream().allMatch(a -> XmlSchema.BENDPOINT.contains(a.name()))) {
            return Optional.empty();
        }
        Map<String, Integer> numbers = new HashMap<>();
        for (Attribute attribute : value.attributes()) {
            Optional<Integer> number = StyleParse.integer(attribute.value());
            if (number.isEmpty()) {
                return Optional.empty();
            }
            numbers.put(attribute.name(), number.get());
        }
        return Optional.of(new Bendpoint(numbers.getOrDefault("startX", 0), numbers.getOrDefault("startY", 0),
                numbers.getOrDefault("endX", 0), numbers.getOrDefault("endY", 0)));
    }

    private static ArchiId archiId(DocumentNode node) {
        return ArchiId.of(node.archiId().value());
    }

    private static IllegalArgumentException unexpected(DocumentNode node, String where) {
        return new IllegalArgumentException("неожиданный узел <" + node.tag() + " id=" + node.archiId() + "> " + where);
    }
}
