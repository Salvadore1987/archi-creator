package uz.salvadore.hamkorbank.archi.interchange.application.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;
import uz.salvadore.hamkorbank.archi.interchange.domain.residue.NodeResidue;
import uz.salvadore.hamkorbank.archi.interchange.domain.residue.ResidueAttribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.residue.ResidueItem;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.ModelRows;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelRowRules;

/**
 * Документ {@code .archimate} — в строки модели ({@code ModelRows}).
 *
 * <p>Каждый узел с идентификатором становится строкой: типизированное — в поля, остальное —
 * в остаток с позициями. Позиция любого содержимого — номер в родителе, разреженный
 * шагом 1000, общий для дочерних строк и для записей остатка: так сборщик вернёт всё
 * в исходном порядке, а вставка между соседями их не перенумерует.
 *
 * <p>Примет ли modeling значение в поле, раскладка спрашивает у его правил формата
 * ({@code ModelRowRules}): принятое уходит в строку, остальное — в остаток буквально.
 * Агрегаты из строк собирает modeling.
 *
 * <p>Идентификаторы Archi сохраняются буквально, без перегенерации, внутренние ключи — новые.
 */
public final class DocumentDecomposer {

    private final Supplier<UUID> uuids;

    /** Ключи строк — из {@code uuids}, по одному на узел документа. */
    public DocumentDecomposer(Supplier<UUID> uuids) {
        this.uuids = uuids;
    }

    /** Раскладывает документ по строкам модели, представлений и остатку XML. */
    public ModelRows decompose(ModelDocument document) {
        return new Run(document).decompose();
    }

    /** Одна раскладка: ключи узлов и накопленные строки. */
    private final class Run {

        private final ModelDocument document;
        private final Map<String, UUID> keys = new HashMap<>();
        private final Map<String, DocumentNode> byArchiId = new HashMap<>();

        private final List<ModelRows.Folder> folders = new ArrayList<>();
        private final List<ModelRows.Element> elements = new ArrayList<>();
        private final List<ModelRows.Relationship> relationships = new ArrayList<>();
        private final List<ModelRows.View> views = new ArrayList<>();

        Run(ModelDocument document) {
            this.document = document;
            for (DocumentNode node : document.allNodes()) {
                keys.put(node.archiId().value(), uuids.get());
                byArchiId.put(node.archiId().value(), node);
            }
        }

        ModelRows decompose() {
            Residue root = new Residue(document.attributes().list(), XmlSchema.ROOT_TYPED);
            String[] documentation = {null};
            List<ModelRows.Property> properties = new ArrayList<>();
            List<DocumentContent> content = document.content();
            for (int i = 0; i < content.size(); i++) {
                long order = ModelRowRules.position(i);
                switch (content.get(i)) {
                    case DocumentNode node when node.tag().equals("folder") -> folder(node, null, order);
                    case DocumentNode node -> throw unexpected(node, Message.of(InterchangeMessages.WHERE_MODEL_ROOT));
                    case DocumentValue value when documentation[0] == null && isDocumentation(value, "purpose") -> {
                        documentation[0] = Objects.requireNonNullElse(value.text(), "");
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
            ModelRows.Header header = new ModelRows.Header(document.archiId().value(), document.name(),
                    documentation[0], document.attributes().get("version").orElse(null), properties, root.encode());
            return new ModelRows(header, folders, elements, relationships, views);
        }

        // ── Папки и их содержимое ───────────────────────────────────

        /** Папка и её содержимое; {@code parent} — {@code null} у корневой. */
        private void folder(DocumentNode node, @Nullable UUID parent, long order) {
            UUID id = key(node);
            Optional<String> type = parent != null ? Optional.empty()
                    : node.attribute("type").filter(ModelRowRules::folderType);
            if (parent == null && type.isEmpty()) {
                throw new InvalidValueException(InterchangeMessages.ROOT_FOLDER_UNTYPED, node.archiId());
            }
            Residue residue = new Residue(node.attributes().list(),
                    type.isPresent() ? XmlSchema.FOLDER_TYPED : Set.of("name", "id"));
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long childOrder = ModelRowRules.position(i);
                switch (content.get(i)) {
                    case DocumentNode child when child.tag().equals("folder") ->
                            folder(child, id, childOrder);
                    case DocumentNode child when child.tag().equals("element") -> modelObject(child, id, childOrder);
                    case DocumentNode child -> throw unexpected(child, Message.of(InterchangeMessages.WHERE_FOLDER));
                    case DocumentValue value -> residue.value(childOrder, value);
                    case RawXmlFragment raw -> residue.fragment(childOrder, raw);
                }
            }
            folders.add(new ModelRows.Folder(id, parent, node.archiId().value(), node.attribute("name").orElse(""),
                    type.orElse(null), order, residue.encode()));
        }

        private void modelObject(DocumentNode node, UUID folder, long order) {
            if (node.archiType().isEmpty()) {
                throw unexpected(node, Message.of(InterchangeMessages.WHERE_WITHOUT, XmlSchema.XSI_TYPE));
            }
            switch (node.kind()) {
                case RELATIONSHIP -> relationship(node, folder, order);
                case VIEW -> view(node, folder, order);
                default -> element(node, folder, order);
            }
        }

        private void element(DocumentNode node, UUID folder, long order) {
            Residue residue = new Residue(node.attributes().list(), XmlSchema.ELEMENT_TYPED);
            Described described = describedContent(node, residue);
            elements.add(new ModelRows.Element(key(node), folder, node.archiId().value(),
                    node.archiType().orElseThrow(), node.attribute("name").orElse(""), described.documentation(),
                    described.properties(), order, residue.encode()));
        }

        private void relationship(DocumentNode node, UUID folder, long order) {
            String accessType = node.attribute("accessType").filter(ModelRowRules::accessType).orElse(null);
            Boolean directed = node.attribute("directed")
                    .filter(v -> v.equals("true") || v.equals("false")).map(Boolean::parseBoolean).orElse(null);
            Residue residue = new Residue(node.attributes().list(), XmlSchema.RELATIONSHIP_TYPED) {
                @Override
                boolean typed(String name, String value) {
                    return switch (name) {
                        case "accessType" -> accessType != null;
                        case "directed" -> directed != null;
                        default -> super.typed(name, value);
                    };
                }
            };
            Described described = describedContent(node, residue);
            relationships.add(new ModelRows.Relationship(key(node), folder, node.archiId().value(),
                    node.archiType().orElseThrow(), concept(node, "source"), concept(node, "target"),
                    node.attribute("name").orElse(null), described.documentation(), accessType, directed,
                    described.properties(), order, residue.encode()));
        }

        /** Ключ конца связи — элемента или другой связи. */
        private UUID concept(DocumentNode node, String attribute) {
            String target = node.attribute(attribute)
                    .orElseThrow(() -> unexpected(node, Message.of(InterchangeMessages.WHERE_WITHOUT, attribute)));
            if (!keys.containsKey(target)) {
                throw new InvalidValueException(InterchangeMessages.RELATIONSHIP_OUTSIDE, node.archiId(), target);
            }
            return keys.get(target);
        }

        /** Документация и свойства элемента или связи; прочее — в остаток. */
        private Described describedContent(DocumentNode node, Residue residue) {
            String[] documentation = {null};
            List<ModelRows.Property> properties = new ArrayList<>();
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long order = ModelRowRules.position(i);
                switch (content.get(i)) {
                    case DocumentValue value when documentation[0] == null
                            && isDocumentation(value, "documentation") -> {
                        documentation[0] = Objects.requireNonNullElse(value.text(), "");
                        residue.slot(order, value);
                    }
                    case DocumentValue value when isProperty(value) -> {
                        properties.add(property(value, order));
                        residue.slot(order, value);
                    }
                    case DocumentValue value -> residue.value(order, value);
                    case RawXmlFragment raw -> residue.fragment(order, raw);
                    case DocumentNode child -> throw unexpected(child,
                            Message.of(InterchangeMessages.WHERE_INSIDE, node.archiId()));
                }
            }
            return new Described(documentation[0], properties);
        }

        // ── Представления ───────────────────────────────────────────

        private void view(DocumentNode node, UUID folder, long order) {
            ViewParts parts = new ViewParts();
            Residue residue = new Residue(node.attributes().list(), XmlSchema.VIEW_TYPED);
            String[] documentation = {null};
            List<ModelRows.Property> properties = new ArrayList<>();
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long childOrder = ModelRowRules.position(i);
                switch (content.get(i)) {
                    case DocumentNode child when child.tag().equals("child") ->
                            viewNode(child, null, childOrder, parts);
                    case DocumentNode child -> throw unexpected(child, Message.of(InterchangeMessages.WHERE_VIEW));
                    case DocumentValue value when documentation[0] == null
                            && isDocumentation(value, "documentation") -> {
                        documentation[0] = Objects.requireNonNullElse(value.text(), "");
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
            views.add(new ModelRows.View(key(node), folder, node.archiId().value(), node.archiType().orElseThrow(),
                    node.attribute("name").orElse(""), documentation[0], node.attribute("viewpoint").orElse(null),
                    properties, order, residue.encode(), parts.nodes, parts.edges));
        }

        /** Узел представления и его дети; {@code parent} — {@code null} у узла в корне представления. */
        private void viewNode(DocumentNode node, @Nullable UUID parent, long order, ViewParts view) {
            UUID id = key(node);
            UUID element = node.attribute("archimateElement")
                    .filter(ref -> byArchiId.containsKey(ref) && isElement(byArchiId.get(ref)))
                    .map(keys::get).orElse(null);
            StyleParse style = new StyleParse(node);
            Residue residue = new Residue(node.attributes().list(), XmlSchema.NODE_TYPED) {
                @Override
                boolean typed(String name, String value) {
                    if (name.equals("archimateElement")) {
                        return element != null;
                    }
                    return XmlSchema.STYLE.contains(name) ? style.typed(name) : super.typed(name, value);
                }
            };
            ModelRows.Box[] bounds = {null};
            String[] text = {null};
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long childOrder = ModelRowRules.position(i);
                switch (content.get(i)) {
                    case DocumentNode child when child.tag().equals("child") ->
                            viewNode(child, id, childOrder, view);
                    case DocumentNode child when child.tag().equals("sourceConnection") ->
                            viewEdge(child, childOrder, view);
                    case DocumentNode child -> throw unexpected(child, Message.of(InterchangeMessages.WHERE_VIEW_NODE));
                    case DocumentValue value -> {
                        Optional<ModelRows.Box> parsed = bounds[0] == null ? parseBounds(value) : Optional.empty();
                        if (parsed.isPresent()) {
                            bounds[0] = parsed.get();
                            residue.slot(childOrder, value);
                        } else if (text[0] == null && isDocumentation(value, "content")) {
                            text[0] = Objects.requireNonNullElse(value.text(), "");
                            residue.slot(childOrder, value);
                        } else {
                            residue.value(childOrder, value);
                        }
                    }
                    case RawXmlFragment raw -> residue.fragment(childOrder, raw);
                }
            }
            view.nodes.add(new ModelRows.Node(id, parent, node.archiId().value(), node.archiType().orElseThrow(),
                    element, bounds[0], style.style(), node.attribute("name").orElse(null), text[0], order,
                    residue.encode()));
        }

        private void viewEdge(DocumentNode node, long order, ViewParts view) {
            UUID id = key(node);
            UUID relationship = node.attribute("archimateRelationship")
                    .filter(ref -> byArchiId.containsKey(ref)
                            && byArchiId.get(ref).kind() == DocumentNode.Kind.RELATIONSHIP)
                    .map(keys::get).orElse(null);
            StyleParse style = new StyleParse(node);
            Residue residue = new Residue(node.attributes().list(), XmlSchema.EDGE_TYPED) {
                @Override
                boolean typed(String name, String value) {
                    if (name.equals("archimateRelationship")) {
                        return relationship != null;
                    }
                    return XmlSchema.STYLE.contains(name) ? style.typed(name) : super.typed(name, value);
                }
            };
            List<BendpointValue> bendpoints = new ArrayList<>();
            List<DocumentContent> content = node.content();
            for (int i = 0; i < content.size(); i++) {
                long childOrder = ModelRowRules.position(i);
                switch (content.get(i)) {
                    case DocumentNode child when child.tag().equals("sourceConnection") ->
                            viewEdge(child, childOrder, view);
                    case DocumentNode child -> throw unexpected(child, Message.of(InterchangeMessages.WHERE_VIEW_EDGE));
                    case DocumentValue value -> parseBendpoint(value).ifPresentOrElse(bendpoint -> {
                        bendpoints.add(bendpoint);
                        residue.slot(childOrder, value);
                    }, () -> residue.value(childOrder, value));
                    case RawXmlFragment raw -> residue.fragment(childOrder, raw);
                }
            }
            view.edges.add(new ModelRows.Edge(id, node.archiId().value(), node.archiType().orElseThrow(),
                    relationship, endpoint(node, "source"), endpoint(node, "target"), bendpoints, style.style(),
                    order, residue.encode()));
        }

        /** Ключ конца ребра — узла или другого ребра. */
        private UUID endpoint(DocumentNode edge, String attribute) {
            String ref = edge.attribute(attribute)
                    .orElseThrow(() -> unexpected(edge, Message.of(InterchangeMessages.WHERE_WITHOUT, attribute)));
            if (!keys.containsKey(ref)) {
                throw new InvalidValueException(InterchangeMessages.EDGE_OUTSIDE, edge.archiId(), ref);
            }
            return keys.get(ref);
        }

        // ── Мелочи ──────────────────────────────────────────────────

        /** Ключ узла: ключи выданы всем узлам документа при создании прохода. */
        private UUID key(DocumentNode node) {
            return Objects.requireNonNull(keys.get(node.archiId().value()), "key");
        }

        private boolean isElement(DocumentNode node) {
            return node.kind() == DocumentNode.Kind.ELEMENT && node.archiType().isPresent();
        }
    }

    /** Узлы и рёбра представления, накопленные обходом его содержимого. */
    private static final class ViewParts {
        final List<ModelRows.Node> nodes = new ArrayList<>();
        final List<ModelRows.Edge> edges = new ArrayList<>();
    }

    /** Документация ({@code null} — нет) и свойства узла. */
    private record Described(@Nullable String documentation, List<ModelRows.Property> properties) {
    }

    /**
     * Остаток одного узла: атрибуты по порядку с метками типизированных и содержимое
     * без идентификатора с позициями.
     */
    private static class Residue {

        private final List<ResidueAttribute> attributes = new ArrayList<>();
        private final List<ResidueItem> items = new ArrayList<>();
        private final Set<String> typedNames;

        Residue(List<Attribute> source, Set<String> typedNames) {
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
        String encode() {
            return new NodeResidue(attributes, items).encode();
        }

    }

    /**
     * Стиль узла или ребра: типизируется только то, что принимают правила формата modeling.
     * Значение, которого нет или которое правила не приняли, — {@code null}.
     */
    private static final class StyleParse {

        private final @Nullable String fillColor;
        private final @Nullable String font;
        private final @Nullable String fontColor;
        private final @Nullable String lineColor;
        private final @Nullable Integer textAlignment;

        StyleParse(DocumentNode node) {
            fillColor = node.attribute("fillColor").filter(ModelRowRules::color).orElse(null);
            font = node.attribute("font").orElse(null);
            fontColor = node.attribute("fontColor").filter(ModelRowRules::color).orElse(null);
            lineColor = node.attribute("lineColor").filter(ModelRowRules::color).orElse(null);
            textAlignment = node.attribute("textAlignment").flatMap(StyleParse::integer)
                    .filter(ModelRowRules::textAlignment).orElse(null);
        }

        boolean typed(String name) {
            return switch (name) {
                case "fillColor" -> fillColor != null;
                case "font" -> font != null;
                case "fontColor" -> fontColor != null;
                case "lineColor" -> lineColor != null;
                case "textAlignment" -> textAlignment != null;
                default -> false;
            };
        }

        StyleValue style() {
            return new StyleValue(fillColor, font, fontColor, lineColor, textAlignment);
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
        return value.tag().equals("property") && value.text() == null
                && value.attributes().list().stream().allMatch(a -> XmlSchema.PROPERTY.contains(a.name()))
                && value.attribute("key").map(ModelRowRules::propertyKey).orElse(true);
    }

    private static ModelRows.Property property(DocumentValue value, long order) {
        return new ModelRows.Property(value.attribute("key").orElse(""), value.attribute("value").orElse(""), order);
    }

    static Optional<ModelRows.Box> parseBounds(DocumentValue value) {
        if (!value.tag().equals("bounds") || value.text() != null
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
        int width = numbers.getOrDefault("width", -1);
        int height = numbers.getOrDefault("height", -1);
        if (!ModelRowRules.size(width) || !ModelRowRules.size(height)) {
            return Optional.empty();
        }
        return Optional.of(new ModelRows.Box(numbers.getOrDefault("x", 0), numbers.getOrDefault("y", 0), width,
                height));
    }

    static Optional<BendpointValue> parseBendpoint(DocumentValue value) {
        if (!value.tag().equals("bendpoint") || value.text() != null
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
        return Optional.of(new BendpointValue(numbers.getOrDefault("startX", 0), numbers.getOrDefault("startY", 0),
                numbers.getOrDefault("endX", 0), numbers.getOrDefault("endY", 0)));
    }

    private static InvalidValueException unexpected(DocumentNode node, Message where) {
        return new InvalidValueException(InterchangeMessages.UNEXPECTED_NODE, node.tag(), node.archiId(), where);
    }
}
