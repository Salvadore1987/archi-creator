package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attributes;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;

/**
 * Что изменится в модели, если одно её состояние заменить другим, — по объектам
 * с идентификатором: модель, папки, элементы, связи, представления.
 *
 * <p>Объекты сопоставляются по {@code archiId}, а не по имени или месту: переименование
 * и перенос в другую папку — изменение полей, а не удаление с добавлением. Узлы и рёбра
 * представления в отчёт поштучно не попадают — любое их изменение даёт поле
 * {@value #DIAGRAM} своего представления. Сдвиг соседей вставкой тоже не изменение:
 * поменялся относительный порядок общих детей — поле {@value #ORDER} у родителя.
 *
 * @param changes по видам объектов, внутри — добавленные, удалённые, изменённые,
 *                затем по имени и идентификатору
 */
public record ModelChanges(List<Change> changes) {

    public static final String TYPE = "type";
    public static final String DOCUMENTATION = "documentation";
    public static final String PROPERTIES = "properties";
    public static final String FOLDER = "folder";
    public static final String DIAGRAM = "diagram";
    public static final String ORDER = "order";
    public static final String RAW_XML = "raw_xml";
    public static final String ID = "id";

    private static final String XSI_TYPE = "xsi:type";
    private static final String ARCHIMATE_PREFIX = "archimate:";
    private static final Comparator<Change> ORDERING = Comparator.comparing(Change::kind)
            .thenComparing(Change::type)
            .thenComparing(Change::name)
            .thenComparing(change -> change.archiId().value());

    /** Вид объекта отчёта. */
    public enum Kind { MODEL, FOLDER, ELEMENT, RELATIONSHIP, VIEW }

    /** Что с объектом. */
    public enum Type { ADDED, REMOVED, CHANGED }

    /**
     * @param archiType тип без префикса {@code archimate:}; у папок и модели пусто
     * @param name      имя после изменения, у удалённого — до
     * @param fields    изменённые поля; у добавленного и удалённого пусто
     */
    public record Change(Kind kind, Type type, ArchiId archiId, Optional<String> archiType, String name,
                         List<String> fields) {

        public Change {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(archiId, "archiId");
            Objects.requireNonNull(archiType, "archiType");
            Objects.requireNonNull(name, "name");
            fields = List.copyOf(fields);
        }
    }

    /** Сколько объектов вида добавлено, удалено и изменено. */
    public record Count(Kind kind, int added, int removed, int changed) {
    }

    public ModelChanges {
        changes = List.copyOf(changes);
    }

    public static ModelChanges between(ModelDocument before, ModelDocument after) {
        Map<ArchiId, Entry> old = index(before);
        Map<ArchiId, Entry> now = index(after);
        List<Change> changes = new ArrayList<>();
        modelChange(before, after, old, now).ifPresent(changes::add);
        for (Entry entry : now.values()) {
            Entry previous = old.get(entry.id());
            if (previous == null) {
                changes.add(entry.change(Type.ADDED, List.of()));
                continue;
            }
            List<String> fields = fields(previous, entry, old, now);
            if (!fields.isEmpty()) {
                changes.add(entry.change(Type.CHANGED, fields));
            }
        }
        old.values().stream().filter(entry -> !now.containsKey(entry.id()))
                .forEach(entry -> changes.add(entry.change(Type.REMOVED, List.of())));
        changes.sort(ORDERING);
        return new ModelChanges(changes);
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /** По строке на каждый вид, в порядке {@link Kind}, — и для видов без изменений. */
    public List<Count> summary() {
        List<Count> counts = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            counts.add(new Count(kind, count(kind, Type.ADDED), count(kind, Type.REMOVED), count(kind, Type.CHANGED)));
        }
        return counts;
    }

    private int count(Kind kind, Type type) {
        return (int) changes.stream().filter(c -> c.kind() == kind && c.type() == type).count();
    }

    /** Объект отчёта: узел, его вид и родитель — папка или корень модели. */
    private record Entry(ArchiId id, Kind kind, DocumentNode node, Optional<ArchiId> parent) {

        Change change(Type type, List<String> fields) {
            return new Change(kind, type, id, node.archiType().map(ModelChanges::bareType),
                    node.attribute("name").orElse(""), fields);
        }
    }

    /** Объекты отчёта по идентификатору в порядке файла; внутрь представлений обход не идёт. */
    private static Map<ArchiId, Entry> index(ModelDocument document) {
        Map<ArchiId, Entry> entries = new LinkedHashMap<>();
        only(document.content(), DocumentNode.class)
                .forEach(node -> collect(node, Optional.empty(), entries));
        return entries;
    }

    private static void collect(DocumentNode node, Optional<ArchiId> parent, Map<ArchiId, Entry> into) {
        Optional<Kind> kind = kind(node);
        if (kind.isEmpty()) {
            return;
        }
        into.putIfAbsent(node.archiId(), new Entry(node.archiId(), kind.get(), node, parent));
        if (kind.get() == Kind.FOLDER) {
            node.children().forEach(child -> collect(child, Optional.of(node.archiId()), into));
        }
    }

    static Optional<Kind> kind(DocumentNode node) {
        return switch (node.tag()) {
            case "folder" -> Optional.of(Kind.FOLDER);
            case "element" -> {
                String type = node.archiType().orElse("");
                yield Optional.of(type.endsWith("Relationship") ? Kind.RELATIONSHIP
                        : type.endsWith("Model") ? Kind.VIEW : Kind.ELEMENT);
            }
            default -> Optional.empty();
        };
    }

    private static Optional<Change> modelChange(ModelDocument before, ModelDocument after,
                                                Map<ArchiId, Entry> old, Map<ArchiId, Entry> now) {
        Set<String> fields = new LinkedHashSet<>();
        if (!before.archiId().equals(after.archiId())) {
            fields.add(ID);
        }
        attributeFields(before.attributes(), after.attributes(), fields);
        valueFields(before.content(), after.content(), fields);
        rawFields(before.content(), after.content(), fields);
        if (orderChanged(childIds(before.content(), old), childIds(after.content(), now))) {
            fields.add(ORDER);
        }
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Change(Kind.MODEL, Type.CHANGED, after.archiId(), Optional.empty(), after.name(),
                List.copyOf(fields)));
    }

    private static List<String> fields(Entry before, Entry after, Map<ArchiId, Entry> old, Map<ArchiId, Entry> now) {
        Set<String> fields = new LinkedHashSet<>();
        attributeFields(before.node().attributes(), after.node().attributes(), fields);
        valueFields(before.node().content(), after.node().content(), fields);
        rawFields(before.node().content(), after.node().content(), fields);
        if (!before.parent().equals(after.parent())) {
            fields.add(FOLDER);
        }
        if (after.kind() == Kind.VIEW
                && !before.node().children().equals(after.node().children())) {
            fields.add(DIAGRAM);
        }
        if (after.kind() == Kind.FOLDER
                && orderChanged(childIds(before.node().content(), old), childIds(after.node().content(), now))) {
            fields.add(ORDER);
        }
        return List.copyOf(fields);
    }

    private static void attributeFields(Attributes before, Attributes after, Set<String> into) {
        Set<String> names = new LinkedHashSet<>();
        before.forEach(a -> names.add(a.name()));
        after.forEach(a -> names.add(a.name()));
        names.remove(ID);
        for (String name : names) {
            if (!before.get(name).equals(after.get(name))) {
                into.add(name.equals(XSI_TYPE) ? TYPE : name);
            }
        }
    }

    /** Значения сравниваются по тегу без позиций: сдвиг соседей значение не меняет. */
    private static void valueFields(List<DocumentContent> before, List<DocumentContent> after, Set<String> into) {
        Map<String, List<ValueKey>> old = values(before);
        Map<String, List<ValueKey>> now = values(after);
        Set<String> tags = new LinkedHashSet<>(old.keySet());
        tags.addAll(now.keySet());
        for (String tag : tags) {
            if (!old.getOrDefault(tag, List.of()).equals(now.getOrDefault(tag, List.of()))) {
                into.add(switch (tag) {
                    case "documentation", "purpose" -> DOCUMENTATION;
                    case "property" -> PROPERTIES;
                    default -> tag;
                });
            }
        }
    }

    private static void rawFields(List<DocumentContent> before, List<DocumentContent> after, Set<String> into) {
        if (!raw(before).equals(raw(after))) {
            into.add(RAW_XML);
        }
    }

    private record ValueKey(List<Attribute> attributes, Optional<String> text) {
    }

    private static Map<String, List<ValueKey>> values(List<DocumentContent> content) {
        Map<String, List<ValueKey>> values = new LinkedHashMap<>();
        only(content, DocumentValue.class).forEach(value -> values
                .computeIfAbsent(value.tag(), t -> new ArrayList<>())
                .add(new ValueKey(value.attributes().list(), value.text())));
        return values;
    }

    private static List<String> raw(List<DocumentContent> content) {
        return only(content, RawXmlFragment.class).stream().map(RawXmlFragment::xml).toList();
    }

    /** Дети-объекты отчёта в порядке файла. */
    private static List<ArchiId> childIds(List<DocumentContent> content, Map<ArchiId, Entry> entries) {
        return only(content, DocumentNode.class).stream().map(DocumentNode::archiId)
                .filter(entries::containsKey).toList();
    }

    /** Относительный порядок детей, общих у двух состояний, — добавленные и удалённые его не меняют. */
    private static boolean orderChanged(List<ArchiId> before, List<ArchiId> after) {
        Set<ArchiId> common = new LinkedHashSet<>(before);
        common.retainAll(Set.copyOf(after));
        return !before.stream().filter(common::contains).toList()
                .equals(after.stream().filter(common::contains).toList());
    }

    private static <T extends DocumentContent> List<T> only(List<DocumentContent> content, Class<T> kind) {
        return content.stream().filter(kind::isInstance).map(kind::cast).toList();
    }

    private static String bareType(String xsiType) {
        return xsiType.startsWith(ARCHIMATE_PREFIX) ? xsiType.substring(ARCHIMATE_PREFIX.length()) : xsiType;
    }
}
