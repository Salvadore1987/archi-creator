package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitYamlFormat.Kind;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attributes;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentOrder;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;

/**
 * Обратная сборка документа модели из дерева {@link GitYamlWriter}: тот же
 * документ как значение, с порядком атрибутов и содержимого, а значит — те же
 * байты писателя {@code .archimate}. На этом стоит восстановление очищенного
 * снимка версии из Git.
 *
 * <p>Формат строгий: незнакомый ключ, повтор позиции среди соседей, ссылка на
 * несуществующую папку — отказ с именем файла, а не догадка. Все скаляры читаются
 * строками: {@code 24} и {@code "24"} — одно и то же.
 */
public final class GitYamlReader {

    private static final int CODE_POINT_LIMIT = 64 * 1024 * 1024;

    /** Документ из файлов каталога модели (пути относительно него). */
    public ModelDocument read(Map<String, byte[]> files) {
        return new Parse(files).document();
    }

    /** Позиции опубликованного дерева — для следующей записи. */
    public GitPositions positions(Map<String, byte[]> files) {
        Parse parse = new Parse(files);
        Map<String, Map<String, Long>> byContainer = new HashMap<>();
        parse.containers.forEach((container, entries) -> {
            Map<String, Long> positions = new HashMap<>();
            entries.forEach(e -> positions.put(e.key, e.position));
            byContainer.put(container, positions);
        });
        return new GitPositions(byContainer);
    }

    /** Запись контейнера до сборки: порядок известен только после сортировки соседей. */
    private record Entry(long position, String key, String file, IntFunction<DocumentContent> build) {
    }

    private static final class Parse {

        private final Map<String, List<Entry>> containers = new HashMap<>();
        private final Attributes rootAttributes;
        private final String rootId;

        Parse(Map<String, byte[]> files) {
            Map<String, Object> model = load(files, MODEL_FILE)
                    .orElseThrow(() -> new InvalidValueException(InterchangeMessages.GIT_FILE_MISSING, MODEL_FILE));
            Fields root = new Fields(MODEL_FILE, model);
            String version = root.required(K_FORMAT_VERSION);
            if (!version.equals(FORMAT_VERSION)) {
                throw new InvalidValueException(InterchangeMessages.GIT_FORMAT_VERSION, version, FORMAT_VERSION);
            }
            rootAttributes = root.attributes(false);
            rootId = rootAttributes.get(A_ID)
                    .orElseThrow(() -> new InvalidValueException(InterchangeMessages.GIT_FIELD_MISSING,
                            MODEL_FILE, K_ID));
            items(root, rootId, Optional.empty());
            root.done();

            Map<String, Object> folderFile = load(files, FOLDERS_FILE).orElse(Map.of());
            Fields folderFields = new Fields(FOLDERS_FILE, folderFile);
            Set<String> folderIds = new HashSet<>();
            for (Map<String, Object> raw : folderFields.maps(K_FOLDERS)) {
                folder(new Fields(FOLDERS_FILE, raw), folderIds);
            }
            folderFields.done();

            files.keySet().stream().sorted()
                    .filter(path -> path.endsWith(EXTENSION) && !path.equals(MODEL_FILE) && !path.equals(FOLDERS_FILE))
                    .forEach(path -> object(path, load(files, path).orElseThrow(), folderIds));
            for (Map.Entry<String, List<Entry>> container : containers.entrySet()) {
                container.getValue().sort(Comparator.comparingLong(Entry::position));
                for (int i = 1; i < container.getValue().size(); i++) {
                    if (container.getValue().get(i).position == container.getValue().get(i - 1).position) {
                        throw new InvalidValueException(InterchangeMessages.GIT_POSITION_REPEATED,
                                container.getValue().get(i).file, container.getValue().get(i).position);
                    }
                }
            }
        }

        ModelDocument document() {
            return new ModelDocument(rootAttributes, build(rootId));
        }

        private List<DocumentContent> build(String container) {
            List<Entry> entries = containers.getOrDefault(container, List.of());
            List<DocumentContent> content = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                content.add(entries.get(i).build.apply(i));
            }
            return content;
        }

        private void add(String container, Entry entry) {
            containers.computeIfAbsent(container, c -> new ArrayList<>()).add(entry);
        }

        private void folder(Fields fields, Set<String> folderIds) {
            String id = fields.required(K_ID);
            Optional<String> parent = fields.optional(K_PARENT);
            long position = fields.position();
            Attributes attributes = fields.attributes(false);
            items(fields, id, Optional.of(id));
            fields.done();
            if (!folderIds.add(id)) {
                throw new InvalidValueException(InterchangeMessages.GIT_ID_REPEATED, FOLDERS_FILE, id);
            }
            String container = parent.orElse(rootId);
            if (parent.isPresent() && !folderIds.contains(parent.get())) {
                throw new InvalidValueException(InterchangeMessages.GIT_FOLDER_UNKNOWN, FOLDERS_FILE, parent.get());
            }
            add(container, new Entry(position, id, FOLDERS_FILE,
                    order -> new DocumentNode(TAG_FOLDER, DocumentOrder.of(order), attributes, build(id))));
        }

        /** Записи корня или папки без идентификатора: позиция и одно из четырёх значений. */
        private void items(Fields owner, String container, Optional<String> parentId) {
            Map<Kind, Integer> ordinals = new EnumMap<>(Kind.class);
            for (Map<String, Object> raw : owner.maps(K_ITEMS)) {
                Fields item = new Fields(owner.file, raw);
                long position = item.position();
                Kind kind = null;
                IntFunction<DocumentContent> build = null;
                for (Kind candidate : List.of(Kind.DOCUMENTATION, Kind.PURPOSE, Kind.PROPERTY, Kind.VALUE, Kind.RAW)) {
                    if (!item.has(candidate.token())) {
                        continue;
                    }
                    if (kind != null) {
                        throw item.invalid(candidate.token());
                    }
                    kind = candidate;
                    build = switch (candidate) {
                        case DOCUMENTATION, PURPOSE -> {
                            String text = item.required(candidate.token());
                            String tag = candidate == Kind.PURPOSE ? TAG_PURPOSE : TAG_DOCUMENTATION;
                            yield order -> textValue(tag, order, text);
                        }
                        case PROPERTY -> {
                            Fields property = item.map(candidate.token());
                            Attributes attributes = propertyAttributes(property);
                            yield order -> new DocumentValue(TAG_PROPERTY, DocumentOrder.of(order), attributes,
                                    Optional.empty());
                        }
                        case VALUE -> genericValue(item.map(candidate.token()));
                        default -> {
                            String xml = item.required(candidate.token());
                            yield order -> new RawXmlFragment(xml, parentId.map(ArchiId::of), DocumentOrder.of(order));
                        }
                    };
                }
                if (kind == null) {
                    throw new InvalidValueException(InterchangeMessages.GIT_FIELD_MISSING, owner.file, K_ITEMS);
                }
                item.done();
                int ordinal = ordinals.merge(kind, 1, Integer::sum);
                add(container, new Entry(position, kind.token() + "#" + ordinal, owner.file, build));
            }
        }

        private void object(String path, Map<String, Object> raw, Set<String> folderIds) {
            Fields fields = new Fields(path, raw);
            Optional<String> folder = fields.optional(K_FOLDER);
            long position = fields.position();
            if (folder.isPresent() && !folderIds.contains(folder.get())) {
                throw new InvalidValueException(InterchangeMessages.GIT_FOLDER_UNKNOWN, path, folder.get());
            }
            String id = fields.required(K_ID);
            IntFunction<DocumentContent> build = node(fields, TAG_ELEMENT);
            fields.done();
            add(folder.orElse(rootId), new Entry(position, id, path, build));
        }
    }

    /** Узел: атрибуты в восстановленном порядке и содержимое по видам. */
    private static IntFunction<DocumentContent> node(Fields fields, String defaultTag) {
        String tag = fields.optional(K_TAG).orElse(defaultTag);
        Attributes attributes = fields.attributes(nested(tag));
        String id = attributes.get(A_ID)
                .orElseThrow(() -> new InvalidValueException(InterchangeMessages.GIT_FIELD_MISSING, fields.file, K_ID));
        Map<Kind, Deque<IntFunction<DocumentContent>>> byKind = new EnumMap<>(Kind.class);
        for (Kind kind : CONTENT_ORDER) {
            if (!fields.has(kind.key)) {
                continue;
            }
            Deque<IntFunction<DocumentContent>> queue = new ArrayDeque<>();
            if (kind.single()) {
                String text = kind == Kind.BOUNDS ? null : fields.required(kind.key);
                queue.add(switch (kind) {
                    case BOUNDS -> points(TAG_BOUNDS, fields.map(kind.key));
                    case TEXT -> order -> textValue(TAG_CONTENT, order, text);
                    case PURPOSE -> order -> textValue(TAG_PURPOSE, order, text);
                    default -> order -> textValue(TAG_DOCUMENTATION, order, text);
                });
            } else if (kind == Kind.RAW) {
                for (String xml : fields.strings(kind.key)) {
                    queue.add(order -> new RawXmlFragment(xml, Optional.of(ArchiId.of(id)), DocumentOrder.of(order)));
                }
            } else {
                for (Map<String, Object> raw : fields.maps(kind.key)) {
                    Fields item = new Fields(fields.file, raw);
                    queue.add(switch (kind) {
                        case PROPERTY -> {
                            Attributes property = propertyAttributes(item);
                            yield order -> new DocumentValue(TAG_PROPERTY, DocumentOrder.of(order), property,
                                    Optional.empty());
                        }
                        case BENDPOINT -> points(TAG_BENDPOINT, item);
                        case CHILD -> node(item, TAG_CHILD);
                        case CONNECTION -> node(item, TAG_CONNECTION);
                        case NODE -> node(item, "");
                        default -> genericValue(item);
                    });
                    item.done();
                }
            }
            byKind.put(kind, queue);
        }
        List<Kind> sequence = new ArrayList<>();
        Optional<List<String>> explicit = fields.optionalStrings(K_CONTENT_ORDER);
        if (explicit.isPresent()) {
            for (String token : explicit.get()) {
                Kind kind = Kind.ofToken(token);
                if (kind == null) {
                    throw fields.invalid(K_CONTENT_ORDER);
                }
                sequence.add(kind);
            }
        } else {
            for (Kind kind : CONTENT_ORDER) {
                for (int i = 0; i < byKind.getOrDefault(kind, new ArrayDeque<>()).size(); i++) {
                    sequence.add(kind);
                }
            }
        }
        List<IntFunction<DocumentContent>> content = new ArrayList<>();
        for (Kind kind : sequence) {
            Deque<IntFunction<DocumentContent>> queue = byKind.get(kind);
            if (queue == null || queue.isEmpty()) {
                throw fields.invalid(K_CONTENT_ORDER);
            }
            content.add(queue.poll());
        }
        if (byKind.values().stream().anyMatch(q -> !q.isEmpty())) {
            throw fields.invalid(K_CONTENT_ORDER);
        }
        if (tag.isEmpty()) {
            throw new InvalidValueException(InterchangeMessages.GIT_FIELD_MISSING, fields.file, K_TAG);
        }
        return order -> {
            List<DocumentContent> built = new ArrayList<>(content.size());
            for (int i = 0; i < content.size(); i++) {
                built.add(content.get(i).apply(i));
            }
            return new DocumentNode(tag, DocumentOrder.of(order), attributes, built);
        };
    }

    private static DocumentValue textValue(String tag, int order, String text) {
        return new DocumentValue(tag, DocumentOrder.of(order), new Attributes(List.of()), Optional.of(text));
    }

    private static Attributes propertyAttributes(Fields property) {
        List<Attribute> list = new ArrayList<>();
        property.optional(K_KEY).ifPresent(k -> list.add(new Attribute(K_KEY, k)));
        property.optional(K_VALUE).ifPresent(v -> list.add(new Attribute(K_VALUE, v)));
        property.done();
        if (list.isEmpty()) {
            throw property.invalid(K_KEY);
        }
        return new Attributes(list);
    }

    private static IntFunction<DocumentContent> points(String tag, Fields fields) {
        Attributes attributes = new Attributes(fields.rest().entrySet().stream()
                .map(e -> new Attribute(e.getKey(), fields.string(e.getKey(), e.getValue()))).toList());
        return order -> new DocumentValue(tag, DocumentOrder.of(order), attributes, Optional.empty());
    }

    private static IntFunction<DocumentContent> genericValue(Fields fields) {
        String tag = fields.required(K_TAG);
        Attributes attributes = new Attributes(fields.optionalMap(K_ATTRIBUTES).map(a -> a.rest().entrySet().stream()
                .map(e -> new Attribute(e.getKey(), a.string(e.getKey(), e.getValue()))).toList()).orElse(List.of()));
        Optional<String> text = fields.optional(K_TEXT);
        fields.done();
        return order -> new DocumentValue(tag, DocumentOrder.of(order), attributes, text);
    }

    @SuppressWarnings("unchecked")
    private static Optional<Map<String, Object>> load(Map<String, byte[]> files, String path) {
        byte[] bytes = files.get(path);
        if (bytes == null) {
            return Optional.empty();
        }
        try {
            Object parsed = yaml().load(new String(bytes, StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new InvalidValueException(InterchangeMessages.GIT_FILE_UNREADABLE, path, "");
            }
            return Optional.of((Map<String, Object>) map);
        } catch (YAMLException e) {
            throw new InvalidValueException(InterchangeMessages.GIT_FILE_UNREADABLE, path, e.getMessage());
        }
    }

    /** SnakeYAML без неявных типов: каждый скаляр — строка, якоря и ссылки запрещены. */
    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setCodePointLimit(CODE_POINT_LIMIT);
        DumperOptions dumper = new DumperOptions();
        return new Yaml(new SafeConstructor(options), new Representer(dumper), dumper, options, new Resolver() {
            @Override
            protected void addImplicitResolvers() {
            }
        });
    }

    /**
     * Поля одного отображения YAML с учётом прочитанного: {@link #done()} отказывает,
     * если в файле остался ключ, которого формат не знает.
     */
    private static final class Fields {

        private final String file;
        private final Map<String, Object> map;
        private final Set<String> read = new HashSet<>();

        Fields(String file, Map<String, Object> map) {
            this.file = file;
            this.map = map;
        }

        boolean has(String key) {
            return map.containsKey(key);
        }

        String required(String key) {
            return optional(key).orElseThrow(() -> new InvalidValueException(InterchangeMessages.GIT_FIELD_MISSING,
                    file, key));
        }

        Optional<String> optional(String key) {
            read.add(key);
            Object value = map.get(key);
            return value == null ? Optional.empty() : Optional.of(string(key, value));
        }

        String string(String key, Object value) {
            if (value instanceof String text) {
                return text;
            }
            throw invalid(key);
        }

        long position() {
            String value = required(K_POSITION);
            try {
                long position = Long.parseLong(value);
                if (position <= 0) {
                    throw invalid(K_POSITION);
                }
                return position;
            } catch (NumberFormatException e) {
                throw invalid(K_POSITION);
            }
        }

        Fields map(String key) {
            return optionalMap(key).orElseThrow(() -> new InvalidValueException(InterchangeMessages.GIT_FIELD_MISSING,
                    file, key));
        }

        @SuppressWarnings("unchecked")
        Optional<Fields> optionalMap(String key) {
            read.add(key);
            Object value = map.get(key);
            if (value == null) {
                return Optional.empty();
            }
            if (value instanceof Map<?, ?> nested) {
                return Optional.of(new Fields(file, (Map<String, Object>) nested));
            }
            throw invalid(key);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> maps(String key) {
            read.add(key);
            Object value = map.get(key);
            if (value == null) {
                return List.of();
            }
            if (value instanceof List<?> list && list.stream().allMatch(Map.class::isInstance)) {
                return (List<Map<String, Object>>) list;
            }
            throw invalid(key);
        }

        List<String> strings(String key) {
            return optionalStrings(key).orElse(List.of());
        }

        Optional<List<String>> optionalStrings(String key) {
            read.add(key);
            Object value = map.get(key);
            if (value == null) {
                return Optional.empty();
            }
            if (value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
                return Optional.of(list.stream().map(String.class::cast).toList());
            }
            throw invalid(key);
        }

        /** Все ещё не прочитанные ключи — как есть: значения точек и атрибутов. */
        Map<String, Object> rest() {
            Map<String, Object> rest = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (!read.contains(k)) {
                    rest.put(k, v);
                }
            });
            read.addAll(rest.keySet());
            return rest;
        }

        /**
         * Атрибуты узла: продвинутые ключи и {@code attributes} в порядке по умолчанию
         * либо по {@code attribute_order}.
         */
        Attributes attributes(boolean nested) {
            Map<String, String> promoted = new LinkedHashMap<>();
            optional(K_ID).ifPresent(v -> promoted.put(A_ID, v));
            Optional<String> type = optional(K_TYPE);
            Optional<String> xsiType = optional(K_XSI_TYPE);
            if (type.isPresent() && xsiType.isPresent()) {
                throw invalid(K_XSI_TYPE);
            }
            type.ifPresent(v -> promoted.put(A_TYPE, v.contains(":") ? v : ARCHIMATE_PREFIX + v));
            xsiType.ifPresent(v -> promoted.put(A_TYPE, v));
            if (!nested) {
                optional(K_NAME).ifPresent(v -> promoted.put(A_NAME, v));
                optional(K_SOURCE).ifPresent(v -> promoted.put(A_SOURCE, v));
                optional(K_TARGET).ifPresent(v -> promoted.put(A_TARGET, v));
            }
            Map<String, String> rest = new LinkedHashMap<>();
            optionalMap(K_ATTRIBUTES).ifPresent(a -> a.rest().forEach((k, v) -> {
                if (promoted(k, nested)) {
                    throw invalid(K_ATTRIBUTES);
                }
                rest.put(k, a.string(k, v));
            }));
            List<String> order = optionalStrings(K_ATTRIBUTE_ORDER)
                    .orElseGet(() -> canonicalOrder(promoted, List.copyOf(rest.keySet())));
            Map<String, String> all = new HashMap<>(promoted);
            all.putAll(rest);
            if (order.size() != all.size() || !all.keySet().containsAll(order)) {
                throw invalid(K_ATTRIBUTE_ORDER);
            }
            return new Attributes(order.stream().map(n -> new Attribute(n, all.get(n))).toList());
        }

        void done() {
            for (String key : map.keySet()) {
                if (!read.contains(key)) {
                    throw new InvalidValueException(InterchangeMessages.GIT_FIELD_UNKNOWN, file, key);
                }
            }
        }

        InvalidValueException invalid(String key) {
            return new InvalidValueException(InterchangeMessages.GIT_FIELD_INVALID, file, key);
        }
    }
}
