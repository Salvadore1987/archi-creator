package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.ARCHIMATE_PREFIX;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.A_ID;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.A_KEY;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.A_NAME;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.A_SOURCE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.A_TARGET;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.A_TYPE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.A_VALUE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.CONTENT_ORDER;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.ELEMENTS_DIR;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.EXTENSION;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.FOLDERS_FILE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.FORMAT_VERSION;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_ATTRIBUTES;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_ATTRIBUTE_ORDER;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_CONTENT_ORDER;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_FOLDER;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_FOLDERS;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_FORMAT_VERSION;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_ID;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_ITEMS;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_KEY;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_NAME;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_PARENT;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_POSITION;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_SOURCE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_TAG;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_TARGET;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_TEXT;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_TYPE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_VALUE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.K_XSI_TYPE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.MODEL_FILE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.RELATIONS_DIR;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_BENDPOINT;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_BOUNDS;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_CHILD;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_CONNECTION;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_CONTENT;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_DOCUMENTATION;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_ELEMENT;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_FOLDER;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_PROPERTY;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.TAG_PURPOSE;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.VIEWS_DIR;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.canonicalOrder;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.itemKey;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.names;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.nested;
import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.promoted;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.Kind;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attributes;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.FileSlug;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;

/**
 * Раскладка документа модели по файлам: {@code model.yaml}, {@code folders.yaml},
 * файл на элемент, на связь и на представление.
 *
 * <p>Детерминирован: один документ и одни прежние позиции — одни байты. Порядок
 * ключей фиксирован, порядок атрибутов и содержимого — из документа, LF, без
 * якорей и переносов. Принадлежность папке — поле {@code folder}, а не каталог;
 * каталоги {@code elements/<слой>/} — только чтобы не держать сотни файлов рядом.
 * Неподдержанное и незнакомое переносится дословно — {@code raw_xml} блоком
 * и {@code attributes}: из дерева {@link GitYamlReader} собирает тот же документ.
 */
public final class GitYamlWriter {

    private final ArchiTypeRegistry types = ArchiTypeRegistry.archimate32();

    /**
     * Файлы модели — пути относительно каталога модели, по алфавиту.
     *
     * @param previous позиции того же каталога в последнем коммите; нет — {@link GitPositions#none()}
     */
    public SortedMap<String, byte[]> write(ModelDocument document, GitPositions previous) {
        Run run = new Run(document, previous);
        run.container(document.archiId().value(), null, document.content());
        SortedMap<String, byte[]> files = new TreeMap<>();
        files.put(MODEL_FILE, YamlEmitter.document(List.of(), run.model()));
        Map<String, Object> folders = new LinkedHashMap<>();
        folders.put(K_FOLDERS, run.folders);
        files.put(FOLDERS_FILE, YamlEmitter.document(List.of(), folders));
        files.putAll(run.objects);
        return files;
    }

    /** Один проход раскладки. */
    private final class Run {

        private final ModelDocument document;
        private final GitPositions previous;
        private final Map<String, String> names = new HashMap<>();
        private final List<Object> rootItems = new ArrayList<>();
        private final List<Object> folders = new ArrayList<>();
        private final Map<String, byte[]> objects = new TreeMap<>();
        private final Set<String> paths = new HashSet<>();

        Run(ModelDocument document, GitPositions previous) {
            this.document = document;
            this.previous = previous;
            for (DocumentNode node : document.allNodes()) {
                node.attribute(A_NAME).ifPresent(name -> names.put(node.archiId().value(), name));
            }
        }

        Map<String, Object> model() {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put(K_FORMAT_VERSION, FORMAT_VERSION);
            attributes(model, document.attributes(), false);
            model.put(K_ITEMS, rootItems);
            return model;
        }

        /**
         * Содержимое корня или папки: у каждой записи — позиция среди соседей.
         *
         * @param folder запись папки в {@code folders.yaml}; {@code null} — корень модели
         */
        void container(String containerId, @Nullable Map<String, Object> folder, List<DocumentContent> content) {
            String folderId = folder == null ? null : (String) folder.get(K_ID);
            List<String> keys = new ArrayList<>(content.size());
            Map<Kind, Integer> ordinals = new EnumMap<>(Kind.class);
            for (DocumentContent item : content) {
                keys.add(switch (item) {
                    case DocumentNode node -> node.archiId().value();
                    default -> {
                        Kind kind = itemKind(item);
                        int ordinal = ordinals.merge(kind, 1, Integer::sum);
                        yield itemKey(kind, ordinal);
                    }
                });
            }
            long[] positions = PositionPlan.plan(keys, previous.of(containerId));
            List<Object> items = folderId == null ? rootItems : new ArrayList<>();
            List<DocumentNode> subfolders = new ArrayList<>();
            List<Map<String, Object>> subfolderEntries = new ArrayList<>();
            for (int i = 0; i < content.size(); i++) {
                String position = Long.toString(positions[i]);
                switch (content.get(i)) {
                    case DocumentNode node when node.tag().equals(TAG_FOLDER) -> {
                        subfolders.add(node);
                        subfolderEntries.add(folderEntry(node, folderId, position));
                    }
                    case DocumentNode node -> object(node, folderId, position);
                    default -> items.add(item(content.get(i), position));
                }
            }
            if (folder != null && !items.isEmpty()) {
                folder.put(K_ITEMS, items);
            }
            for (int i = 0; i < subfolders.size(); i++) {
                folders.add(subfolderEntries.get(i));
                container(subfolders.get(i).archiId().value(), subfolderEntries.get(i),
                        subfolders.get(i).content());
            }
        }

        /** Запись папки; {@code parent} — {@code null} у папки в корне модели. */
        private Map<String, Object> folderEntry(DocumentNode folder, @Nullable String parent, String position) {
            Map<String, Object> entry = new LinkedHashMap<>();
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes(attributes, folder.attributes(), false);
            entry.put(K_ID, attributes.remove(K_ID));
            if (attributes.containsKey(K_NAME)) {
                entry.put(K_NAME, attributes.remove(K_NAME));
            }
            if (parent != null) {
                entry.put(K_PARENT, parent);
            }
            entry.put(K_POSITION, position);
            entry.putAll(attributes);
            return entry;
        }

        private Map<String, Object> item(DocumentContent content, String position) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(K_POSITION, position);
            Kind kind = itemKind(content);
            item.put(kind.token(), switch (content) {
                case RawXmlFragment raw -> raw.xml();
                case DocumentValue value when kind == Kind.PROPERTY -> property(value);
                case DocumentValue value when kind == Kind.VALUE -> genericValue(value);
                case DocumentValue value -> Objects.requireNonNull(value.text());
                case DocumentNode node -> throw new IllegalStateException(node.tag());
            });
            return item;
        }

        /** Вид записи корня или папки: у них своё содержимое, кроме узлов, — только эти четыре. */
        private Kind itemKind(DocumentContent item) {
            return switch (item) {
                case RawXmlFragment raw -> Kind.RAW;
                case DocumentValue value when regularText(value, TAG_DOCUMENTATION) -> Kind.DOCUMENTATION;
                case DocumentValue value when regularText(value, TAG_PURPOSE) -> Kind.PURPOSE;
                case DocumentValue value when regularProperty(value) -> Kind.PROPERTY;
                case DocumentValue value -> Kind.VALUE;
                case DocumentNode node -> Kind.NODE;
            };
        }

        /** Файл объекта; {@code folderId} — {@code null} у объекта в корне модели. */
        private void object(DocumentNode node, @Nullable String folderId, String position) {
            Map<String, Object> placement = new LinkedHashMap<>();
            if (folderId != null) {
                placement.put(K_FOLDER, folderId);
            }
            placement.put(K_POSITION, position);
            Map<String, Object> body = node(node, placement, TAG_ELEMENT);
            List<String> comments = relationshipEnds(node).map(List::of).orElse(List.of());
            objects.put(path(node), YamlEmitter.document(comments, body));
        }

        /** Заголовок файла связи: имена концов, иначе {@code git log -p} показывает одни идентификаторы. */
        private Optional<String> relationshipEnds(DocumentNode node) {
            if (!isRelationship(node)) {
                return Optional.empty();
            }
            Optional<String> source = node.attribute(A_SOURCE);
            Optional<String> target = node.attribute(A_TARGET);
            if (source.isEmpty() || target.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(endName(source.get()) + " → " + endName(target.get()));
        }

        private String endName(String id) {
            return names.getOrDefault(id, id).replaceAll("\\s+", " ").strip();
        }

        private String path(DocumentNode node) {
            String type = node.archiType().orElse("");
            String simple = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
            String directory;
            String label;
            if (isRelationship(node)) {
                directory = RELATIONS_DIR;
                String kind = kebab(simple.substring(0, simple.length() - "Relationship".length()));
                label = node.attribute(A_NAME).filter(n -> !n.isBlank()).map(n -> kind + " " + n).orElse(kind);
            } else if (node.kind() == DocumentNode.Kind.VIEW) {
                directory = VIEWS_DIR;
                label = node.attribute(A_NAME).filter(n -> !n.isBlank()).orElse(kebab(simple));
            } else {
                directory = ELEMENTS_DIR + types.findByXsiType(type)
                        .map(d -> d.layer().name().toLowerCase(Locale.ROOT)).orElse("other") + "/";
                label = node.attribute(A_NAME).filter(n -> !n.isBlank()).orElse(kebab(simple));
            }
            String base = directory + FileSlug.of(label, ArchiId.of(node.archiId().value()));
            String path = base + EXTENSION;
            for (int n = 2; !paths.add(path); n++) {
                path = base + "-" + n + EXTENSION;
            }
            return path;
        }

        private boolean isRelationship(DocumentNode node) {
            return node.kind() == DocumentNode.Kind.RELATIONSHIP;
        }
    }

    /**
     * Узел целиком: атрибуты, затем содержимое по видам. {@code placement} —
     * {@code folder} и {@code position} файла верхнего уровня, у вложенных пусто.
     */
    private static Map<String, Object> node(DocumentNode node, Map<String, Object> placement, String expectedTag) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes(attributes, node.attributes(), nested(node.tag()));
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of(K_ID, K_TYPE, K_XSI_TYPE, K_NAME, K_SOURCE, K_TARGET)) {
            if (attributes.containsKey(key)) {
                out.put(key, attributes.remove(key));
            }
        }
        if (!node.tag().equals(expectedTag)) {
            out.put(K_TAG, node.tag());
        }
        out.putAll(placement);
        out.putAll(attributes);
        content(out, node.content());
        return out;
    }

    /** Значение под ключом, если оно есть; {@code null} — атрибута нет. */
    private static void putPresent(Map<String, Object> out, String key, @Nullable String value) {
        if (value != null) {
            out.put(key, value);
        }
    }

    /**
     * Атрибуты узла или корня: продвинутые — своими ключами, прочие — {@code attributes}
     * в порядке файла, нестандартный порядок — {@code attribute_order}.
     */
    private static void attributes(Map<String, Object> out, Attributes attributes, boolean nested) {
        Map<String, String> promoted = new LinkedHashMap<>();
        Map<String, String> rest = new LinkedHashMap<>();
        for (Attribute attribute : attributes) {
            (promoted(attribute.name(), nested) ? promoted : rest).put(attribute.name(), attribute.value());
        }
        putPresent(out, K_ID, promoted.get(A_ID));
        String type = promoted.get(A_TYPE);
        if (type != null) {
            if (type.startsWith(ARCHIMATE_PREFIX) && type.indexOf(':', ARCHIMATE_PREFIX.length()) < 0) {
                out.put(K_TYPE, type.substring(ARCHIMATE_PREFIX.length()));
            } else if (type.contains(":")) {
                out.put(K_TYPE, type);
            } else {
                out.put(K_XSI_TYPE, type);
            }
        }
        putPresent(out, K_NAME, promoted.get(A_NAME));
        putPresent(out, K_SOURCE, promoted.get(A_SOURCE));
        putPresent(out, K_TARGET, promoted.get(A_TARGET));
        if (!rest.isEmpty()) {
            out.put(K_ATTRIBUTES, new LinkedHashMap<>(rest));
        }
        List<String> actual = names(attributes.list());
        if (!actual.equals(canonicalOrder(promoted, List.copyOf(rest.keySet())))) {
            out.put(K_ATTRIBUTE_ORDER, actual);
        }
    }

    /** Содержимое узла по видам; порядок, отличный от {@link GitYamlFormat#CONTENT_ORDER}, — списком. */
    private static void content(Map<String, Object> out, List<DocumentContent> content) {
        Map<Kind, List<Object>> byKind = new EnumMap<>(Kind.class);
        List<Kind> actual = new ArrayList<>();
        for (DocumentContent item : content) {
            Kind kind = nodeKind(item, byKind);
            actual.add(kind);
            byKind.computeIfAbsent(kind, k -> new ArrayList<>()).add(encode(item, kind));
        }
        for (Kind kind : CONTENT_ORDER) {
            List<Object> values = byKind.get(kind);
            if (values != null) {
                out.put(kind.key, kind.single() ? values.getFirst() : values);
            }
        }
        List<Kind> canonical = actual.stream().sorted().toList();
        if (!actual.equals(canonical)) {
            out.put(K_CONTENT_ORDER, actual.stream().map(Kind::token).toList());
        }
    }

    private static Kind nodeKind(DocumentContent item, Map<Kind, List<Object>> seen) {
        Kind kind = switch (item) {
            case RawXmlFragment raw -> Kind.RAW;
            case DocumentNode node when node.tag().equals(TAG_CHILD) -> Kind.CHILD;
            case DocumentNode node when node.tag().equals(TAG_CONNECTION) -> Kind.CONNECTION;
            case DocumentNode node -> Kind.NODE;
            case DocumentValue value when regularText(value, TAG_DOCUMENTATION) -> Kind.DOCUMENTATION;
            case DocumentValue value when regularText(value, TAG_PURPOSE) -> Kind.PURPOSE;
            case DocumentValue value when regularText(value, TAG_CONTENT) -> Kind.TEXT;
            case DocumentValue value when regularProperty(value) -> Kind.PROPERTY;
            case DocumentValue value when regularPoints(value, TAG_BOUNDS) -> Kind.BOUNDS;
            case DocumentValue value when regularPoints(value, TAG_BENDPOINT) -> Kind.BENDPOINT;
            case DocumentValue value -> Kind.VALUE;
        };
        return kind.single() && seen.containsKey(kind) ? Kind.VALUE : kind;
    }

    private static Object encode(DocumentContent item, Kind kind) {
        return switch (item) {
            case RawXmlFragment raw -> raw.xml();
            case DocumentNode node when kind == Kind.CHILD -> node(node, Map.of(), TAG_CHILD);
            case DocumentNode node when kind == Kind.CONNECTION -> node(node, Map.of(), TAG_CONNECTION);
            case DocumentNode node -> node(node, Map.of(), "");
            case DocumentValue value -> switch (kind) {
                case DOCUMENTATION, PURPOSE, TEXT -> Objects.requireNonNull(value.text());
                case PROPERTY -> property(value);
                case BOUNDS, BENDPOINT -> points(value);
                default -> genericValue(value);
            };
        };
    }

    private static boolean regularText(DocumentValue value, String tag) {
        return value.tag().equals(tag) && value.attributes().list().isEmpty() && value.text() != null;
    }

    /** {@code <property key value/>} — атрибуты из этих двух и в этом порядке, без текста. */
    private static boolean regularProperty(DocumentValue value) {
        List<String> names = names(value.attributes().list());
        return value.tag().equals(TAG_PROPERTY) && value.text() == null && !names.isEmpty()
                && List.of(A_KEY, A_VALUE).containsAll(names) && (names.size() == 1 || names.getFirst().equals(A_KEY));
    }

    private static boolean regularPoints(DocumentValue value, String tag) {
        return value.tag().equals(tag) && value.text() == null && !value.attributes().list().isEmpty();
    }

    private static Map<String, Object> property(DocumentValue value) {
        Map<String, Object> property = new LinkedHashMap<>();
        value.attribute(A_KEY).ifPresent(k -> property.put(K_KEY, k));
        value.attribute(A_VALUE).ifPresent(v -> property.put(K_VALUE, v));
        return property;
    }

    private static Object points(DocumentValue value) {
        Map<String, String> entries = new LinkedHashMap<>();
        value.attributes().forEach(a -> entries.put(a.name(), a.value()));
        if (YamlEmitter.flowable(entries)) {
            return new YamlEmitter.Flow(entries);
        }
        return new LinkedHashMap<String, Object>(entries);
    }

    private static Map<String, Object> genericValue(DocumentValue value) {
        Map<String, Object> generic = new LinkedHashMap<>();
        generic.put(K_TAG, value.tag());
        if (!value.attributes().list().isEmpty()) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            value.attributes().forEach(a -> attributes.put(a.name(), a.value()));
            generic.put(K_ATTRIBUTES, attributes);
        }
        if (value.text() != null) {
            generic.put(K_TEXT, value.text());
        }
        return generic;
    }

    /** {@code ApplicationComponent} → {@code application-component}: слаг из типа, когда имени нет. */
    private static String kebab(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}
