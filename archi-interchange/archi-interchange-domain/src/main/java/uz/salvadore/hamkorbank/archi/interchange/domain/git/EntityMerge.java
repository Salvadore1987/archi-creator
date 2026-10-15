package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attributes;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentOrder;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges.Kind;

/**
 * Слияние трёх состояний модели — общего предка, «нашего» и «их» — по объектам
 * с идентификатором и их полям, а не по строкам файла.
 *
 * <p>Объект — модель, папка, элемент, связь, представление; ключ — {@code archiId}.
 * Поля: каждый атрибут отдельно, {@value ModelChanges#DOCUMENTATION}, {@value ModelChanges#PROPERTIES}
 * списком целиком (порядок значим), {@value ModelChanges#RAW_XML}, {@value ModelChanges#FOLDER} —
 * родитель, у представления {@value ModelChanges#DIAGRAM} — узлы и рёбра целиком. Поле, изменённое
 * в одной ветви, берётся из неё; изменённое в обеих по-разному — конфликт по полю. Объект,
 * удалённый в одной ветви и изменённый в другой, — конфликт объекта с полем {@value #REMOVAL}.
 * Конфликт без выбора разрешается в пользу «нашего»: результат строится всегда, а применим
 * он, только когда {@link Result#resolved()}.
 *
 * <p>Порядок детей конфликтом не бывает: основа — порядок ветви, которая его меняла, а добавленное
 * другой ветвью встаёт после всех соседей, стоявших перед ним в этой ветви. После разрешения
 * идёт каскад: объект без папки, связь без конца, узел представления без элемента, ребро без
 * концов удаляются, и каждое такое удаление — запись {@link Cascade}.
 */
public final class EntityMerge {

    /** Поле конфликта «удалён в одной ветви, изменён в другой». */
    public static final String REMOVAL = "*";

    private static final String ID = "id";
    private static final String XSI_TYPE = "xsi:type";
    private static final String NAME = "name";
    private static final String ATTRIBUTE = "@";
    private static final String SOURCE = "source";
    private static final String TARGET = "target";
    private static final String ELEMENT_REF = "archimateElement";
    private static final String RELATIONSHIP_REF = "archimateRelationship";
    private static final String MODEL_REF = "model";
    private static final String TARGET_CONNECTIONS = "targetConnections";
    private static final String CONNECTION_TAG = "sourceConnection";

    /** Чья сторона берётся. */
    public enum Side { OURS, THEIRS }

    /** Что удалено каскадом. */
    public enum CascadeKind { FOLDER, ELEMENT, RELATIONSHIP, VIEW, VIEW_NODE, VIEW_CONNECTION }

    /** Почему удалено каскадом. */
    public enum Reason {
        FOLDER_REMOVED, RELATION_ENDPOINT_REMOVED, DIAGRAM_REFERENCE_REMOVED, CONNECTION_ENDPOINT_REMOVED,
        FOLDER_CYCLE
    }

    /**
     * Конфликт: поле, изменённое в обеих ветвях по-разному, или удаление против правки.
     *
     * @param id         адрес {@code <archiId>:<поле>}, ключ выбора
     * @param base       значение в общем предке; нет — пусто
     * @param ours       «наше»; удалено — пусто. У удаления против правки — изменённые поля
     * @param resolution сделанный выбор; нет — результат взят «нашим»
     */
    public record Conflict(String id, Kind kind, ArchiId archiId, Optional<String> archiType, String name,
                           String field, Optional<String> base, Optional<String> ours, Optional<String> theirs,
                           Optional<Side> resolution) {

        public Conflict {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(archiId, "archiId");
            Objects.requireNonNull(archiType, "archiType");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(ours, "ours");
            Objects.requireNonNull(theirs, "theirs");
            Objects.requireNonNull(resolution, "resolution");
        }
    }

    /**
     * Удаление как следствие разрешения, а не выбор.
     *
     * @param view представление узла или ребра; у объектов модели пусто
     */
    public record Cascade(CascadeKind kind, ArchiId archiId, String name, Reason reason, Optional<ArchiId> view) {
    }

    /** Слитый документ, конфликты в порядке объектов и каскад. */
    public record Result(ModelDocument document, List<Conflict> conflicts, List<Cascade> cascade) {

        public Result {
            Objects.requireNonNull(document, "document");
            conflicts = List.copyOf(conflicts);
            cascade = List.copyOf(cascade);
        }

        /** Выбор сделан по каждому конфликту. */
        public boolean resolved() {
            return conflicts.stream().allMatch(c -> c.resolution().isPresent());
        }

        /** Адреса конфликтов, по которым ещё нет выбора. */
        public List<String> unresolved() {
            return conflicts.stream().filter(c -> c.resolution().isEmpty()).map(Conflict::id).toList();
        }

        /** Выборы по адресам, которых среди конфликтов нет: отчёт, по которому выбирали, устарел. */
        public List<String> unknown(Set<String> resolutions) {
            Set<String> ids = conflicts.stream().map(Conflict::id).collect(Collectors.toSet());
            return resolutions.stream().filter(id -> !ids.contains(id)).sorted().toList();
        }
    }

    private EntityMerge() {
    }

    /** Сливает три состояния модели по объектам и полям с учётом выборов по конфликтам. */
    public static Result merge(ModelDocument base, ModelDocument ours, ModelDocument theirs,
                               Map<String, Side> resolutions) {
        return new Merger(base, ours, theirs, Map.copyOf(resolutions)).run();
    }

    /** Объект одной стороны: модель (корень) или узел; {@code parent} пуст у корня и у детей корня. */
    private record Obj(ArchiId id, Kind kind, String tag, Attributes attributes, List<DocumentContent> content,
                       Optional<ArchiId> parent) {

        String name() {
            return attributes.get(NAME).orElse("");
        }

        Optional<String> archiType() {
            return attributes.get(XSI_TYPE).map(EntityMerge::bareType);
        }
    }

    /** Ключ места в содержимом: узел, значение поля по номеру внутри поля, фрагмент по номеру. */
    private sealed interface Key permits NodeKey, ValueKey, RawKey {
    }

    private record NodeKey(ArchiId id) implements Key {
    }

    private record ValueKey(String field, int index) implements Key {
    }

    private record RawKey(int index) implements Key {
    }

    /** Значение без позиций: сдвиг соседей значение не меняет. */
    private record Shape(String tag, List<Attribute> attributes, Optional<String> text, List<Shape> content) {
    }

    /** Состояние одной стороны: объекты по идентификатору в порядке файла. */
    private static final class State {

        final ModelDocument document;
        final Map<ArchiId, Obj> objects = new LinkedHashMap<>();

        State(ModelDocument document) {
            this.document = document;
            objects.put(document.archiId(), new Obj(document.archiId(), Kind.MODEL, "", document.attributes(),
                    document.content(), Optional.empty()));
            nodes(document.content()).forEach(node -> collect(node, Optional.empty()));
        }

        private void collect(DocumentNode node, Optional<ArchiId> parent) {
            Optional<Kind> kind = ModelChanges.kind(node);
            if (kind.isEmpty()) {
                return;
            }
            objects.putIfAbsent(node.archiId(), new Obj(node.archiId(), kind.get(), node.tag(), node.attributes(),
                    node.content(), parent));
            if (kind.get() == Kind.FOLDER) {
                node.children().forEach(child -> collect(child, Optional.of(node.archiId())));
            }
        }

        Optional<Obj> find(ArchiId id) {
            return Optional.ofNullable(objects.get(id));
        }

        /** Прямые дети-объекты контейнера: корень — пустой идентификатор. */
        Set<ArchiId> members(Optional<ArchiId> container) {
            Set<ArchiId> members = new LinkedHashSet<>();
            objects.values().forEach(o -> {
                if (o.kind() != Kind.MODEL && o.parent().equals(container)) {
                    members.add(o.id());
                }
            });
            return members;
        }

        String nameOf(Optional<ArchiId> folder) {
            return folder.flatMap(this::find).map(Obj::name).orElseGet(document::name);
        }
    }

    /** Объект результата: откуда взято каждое поле. */
    private record Merged(ArchiId id, Kind kind, Optional<Obj> ours, Optional<Obj> theirs, Map<String, Side> fields) {

        Obj side(Side side) {
            return (side == Side.OURS ? ours : theirs).orElseThrow();
        }

        Obj primary() {
            return ours.orElseGet(theirs::orElseThrow);
        }

        Side sideOf(String field) {
            return fields.getOrDefault(field, ours.isPresent() ? Side.OURS : Side.THEIRS);
        }

        Optional<ArchiId> parent() {
            return side(sideOf(ModelChanges.FOLDER)).parent();
        }

        Optional<String> attribute(String name) {
            Side side = sideOf(ATTRIBUTE + name);
            return (side == Side.OURS ? ours : theirs).flatMap(o -> o.attributes().get(name));
        }

        String name() {
            return attribute(NAME).orElse("");
        }

        Merged with(String field, Side side) {
            Map<String, Side> changed = new LinkedHashMap<>(fields);
            changed.put(field, side);
            return new Merged(id, kind, ours, theirs, changed);
        }
    }

    private static final class Merger {

        private final State base;
        private final State ours;
        private final State theirs;
        private final Map<String, Side> resolutions;
        private final Map<ArchiId, Merged> result = new LinkedHashMap<>();
        private final List<Conflict> conflicts = new ArrayList<>();
        private final List<Cascade> cascade = new ArrayList<>();

        Merger(ModelDocument base, ModelDocument ours, ModelDocument theirs, Map<String, Side> resolutions) {
            this.base = new State(base);
            this.ours = new State(ours);
            this.theirs = new State(theirs);
            this.resolutions = resolutions;
        }

        Result run() {
            Set<ArchiId> ids = new LinkedHashSet<>(ours.objects.keySet());
            ids.addAll(theirs.objects.keySet());
            ids.remove(theirs.document.archiId());
            ids.remove(ours.document.archiId());
            mergeModel();
            for (ArchiId id : ids) {
                mergeObject(id);
            }
            breakFolderCycles();
            cascadeFolders();
            cascadeRelationships();
            Set<ArchiId> present = result.keySet();
            Merged model = result.get(ours.document.archiId());
            List<DocumentContent> content = content(model, Optional.empty(), present);
            ModelDocument document = new ModelDocument(attributes(model), content);
            return new Result(document, conflicts, cascade);
        }

        // ── Объекты и поля ─────────────────────────────────────────

        private void mergeModel() {
            ArchiId id = ours.document.archiId();
            Obj o = ours.objects.get(id);
            Obj t = theirs.objects.get(theirs.document.archiId());
            Obj b = base.objects.get(base.document.archiId());
            Map<String, Side> fields = sides(id, Kind.MODEL, o.name(), Optional.empty(), Optional.of(b),
                    Optional.of(o), Optional.of(t));
            result.put(id, new Merged(id, Kind.MODEL, Optional.of(o), Optional.of(t), fields));
        }

        private void mergeObject(ArchiId id) {
            Optional<Obj> b = base.find(id);
            Optional<Obj> o = ours.find(id);
            Optional<Obj> t = theirs.find(id);
            Kind kind = o.or(() -> t).orElseThrow().kind();
            if (b.isEmpty() || o.isPresent() && t.isPresent()) {
                if (o.isPresent() && t.isPresent()) {
                    Obj named = o.get();
                    result.put(id, new Merged(id, kind, o, t,
                            sides(id, kind, named.name(), named.archiType(), b, o, t)));
                } else {
                    result.put(id, new Merged(id, kind, o, t, Map.of()));
                }
                return;
            }
            boolean oursChanged = o.isPresent() && changed(b.get(), o.get(), ours);
            boolean theirsChanged = t.isPresent() && changed(b.get(), t.get(), theirs);
            if (o.isEmpty() && !theirsChanged || t.isEmpty() && !oursChanged) {
                return;
            }
            Obj kept = o.or(() -> t).orElseThrow();
            String conflictId = conflictId(id, REMOVAL);
            Optional<Side> choice = Optional.ofNullable(resolutions.get(conflictId));
            Optional<String> oursValue = o.map(obj -> changedFields(b.get(), obj));
            Optional<String> theirsValue = t.map(obj -> changedFields(b.get(), obj));
            conflicts.add(new Conflict(conflictId, kind, id, kept.archiType(), kept.name(), REMOVAL,
                    Optional.of(b.get().name()), oursValue, theirsValue, choice));
            Side side = choice.orElse(Side.OURS);
            boolean keep = side == Side.OURS ? o.isPresent() : t.isPresent();
            if (keep) {
                result.put(id, new Merged(id, kind, side == Side.OURS ? o : Optional.empty(),
                        side == Side.THEIRS ? t : Optional.empty(), Map.of()));
            }
        }

        /** Откуда взять каждое поле; спорные — по выбору, без выбора «наше». */
        private Map<String, Side> sides(ArchiId id, Kind kind, String name, Optional<String> archiType,
                                         Optional<Obj> b, Optional<Obj> o, Optional<Obj> t) {
            Map<String, Object> baseFields = b.map(EntityMerge::fields).orElseGet(Map::of);
            Map<String, Object> oursFields = fields(o.orElseThrow());
            Map<String, Object> theirsFields = fields(t.orElseThrow());
            Set<String> names = new LinkedHashSet<>(oursFields.keySet());
            names.addAll(theirsFields.keySet());
            names.addAll(baseFields.keySet());
            Map<String, Side> sides = new LinkedHashMap<>();
            for (String field : names) {
                Object before = baseFields.get(field);
                Object mine = oursFields.get(field);
                Object their = theirsFields.get(field);
                if (Objects.equals(mine, their) || Objects.equals(their, before)) {
                    sides.put(field, Side.OURS);
                } else if (Objects.equals(mine, before)) {
                    sides.put(field, Side.THEIRS);
                } else {
                    String display = display(field);
                    String conflictId = conflictId(id, display);
                    Optional<Side> choice = Optional.ofNullable(resolutions.get(conflictId));
                    conflicts.add(new Conflict(conflictId, kind, id, archiType, name, display,
                            describe(field, b, base), describe(field, o, ours), describe(field, t, theirs), choice));
                    sides.put(field, choice.orElse(Side.OURS));
                }
            }
            return sides;
        }

        /**
         * Изменён ли объект стороны: поля, а у папки ещё и содержимое — новый или изменённый
         * объект внутри. Удаление папки иначе молча унесло бы чужую работу в ней.
         */
        private boolean changed(Obj before, Obj after, State side) {
            if (!fields(before).equals(fields(after))) {
                return true;
            }
            if (before.kind() != Kind.FOLDER) {
                return false;
            }
            Set<ArchiId> old = base.members(Optional.of(before.id()));
            for (ArchiId member : side.members(Optional.of(after.id()))) {
                if (!old.contains(member) || changed(base.objects.get(member), side.objects.get(member), side)) {
                    return true;
                }
            }
            return false;
        }

        /** Изменённые поля одной стороны через запятую — значение конфликта удаления. */
        private String changedFields(Obj before, Obj after) {
            Map<String, Object> old = fields(before);
            Map<String, Object> now = fields(after);
            Set<String> names = new LinkedHashSet<>(old.keySet());
            names.addAll(now.keySet());
            return names.stream().filter(n -> !Objects.equals(old.get(n), now.get(n))).map(EntityMerge::display)
                    .collect(Collectors.joining(", "));
        }

        private Optional<String> describe(String field, Optional<Obj> obj, State state) {
            if (obj.isEmpty()) {
                return Optional.empty();
            }
            Obj o = obj.get();
            if (field.startsWith(ATTRIBUTE)) {
                return o.attributes().get(field.substring(ATTRIBUTE.length()));
            }
            return switch (field) {
                case ModelChanges.FOLDER -> Optional.of(state.nameOf(o.parent()));
                case ModelChanges.DIAGRAM -> Optional.of(Integer.toString(diagramObjects(nodes(o.content()))));
                case ModelChanges.RAW_XML -> Optional.of(raws(o.content()).stream().map(RawXmlFragment::xml)
                        .collect(Collectors.joining("\n")));
                case ModelChanges.PROPERTIES -> Optional.of(values(o.content(), field).stream()
                        .map(v -> v.attribute("key").orElse("") + " = " + v.attribute("value").orElse(""))
                        .collect(Collectors.joining("\n")));
                default -> Optional.of(values(o.content(), field).stream()
                        .map(v -> v.text().orElse("")).collect(Collectors.joining("\n")));
            };
        }

        // ── Каскад ─────────────────────────────────────────────────

        /** Папки, переложенные ветвями друг в друга: перенос «их» откатывается к нашему. */
        private void breakFolderCycles() {
            boolean found = true;
            while (found) {
                found = false;
                for (Merged folder : List.copyOf(result.values())) {
                    if (folder.kind() != Kind.FOLDER) {
                        continue;
                    }
                    List<ArchiId> cycle = cycle(folder.id());
                    if (cycle.isEmpty()) {
                        continue;
                    }
                    found = true;
                    Optional<Merged> revertible = cycle.stream().map(result::get)
                            .filter(m -> m.sideOf(ModelChanges.FOLDER) == Side.THEIRS && m.ours().isPresent())
                            .findFirst();
                    if (revertible.isPresent()) {
                        Merged m = revertible.get();
                        result.put(m.id(), m.with(ModelChanges.FOLDER, Side.OURS));
                        cascade.add(new Cascade(CascadeKind.FOLDER, m.id(), m.name(), Reason.FOLDER_CYCLE,
                                Optional.empty()));
                    } else {
                        Merged m = result.remove(cycle.getFirst());
                        cascade.add(new Cascade(CascadeKind.FOLDER, m.id(), m.name(), Reason.FOLDER_CYCLE,
                                Optional.empty()));
                    }
                    break;
                }
            }
        }

        private List<ArchiId> cycle(ArchiId start) {
            List<ArchiId> path = new ArrayList<>();
            Optional<ArchiId> current = Optional.of(start);
            while (current.isPresent() && result.containsKey(current.get())) {
                int seen = path.indexOf(current.get());
                if (seen >= 0) {
                    return path.subList(seen, path.size());
                }
                path.add(current.get());
                current = result.get(current.get()).parent();
            }
            return List.of();
        }

        /** Объект, чьей папки в результате нет, уходит вместе с ней. */
        private void cascadeFolders() {
            boolean removed = true;
            while (removed) {
                removed = false;
                for (Merged m : List.copyOf(result.values())) {
                    if (m.kind() == Kind.MODEL) {
                        continue;
                    }
                    Optional<ArchiId> parent = m.parent();
                    if (parent.isPresent() && !result.containsKey(parent.get())) {
                        result.remove(m.id());
                        cascade.add(new Cascade(cascadeKind(m.kind()), m.id(), m.name(), Reason.FOLDER_REMOVED,
                                Optional.empty()));
                        removed = true;
                    }
                }
            }
        }

        /** Связь без источника или цели удаляется — и связь на неё тоже. */
        private void cascadeRelationships() {
            boolean removed = true;
            while (removed) {
                removed = false;
                for (Merged m : List.copyOf(result.values())) {
                    if (m.kind() != Kind.RELATIONSHIP) {
                        continue;
                    }
                    boolean dangling = m.attribute(SOURCE).map(ArchiId::of).filter(result::containsKey).isEmpty()
                            || m.attribute(TARGET).map(ArchiId::of).filter(result::containsKey).isEmpty();
                    if (dangling) {
                        result.remove(m.id());
                        cascade.add(new Cascade(CascadeKind.RELATIONSHIP, m.id(), m.name(),
                                Reason.RELATION_ENDPOINT_REMOVED, Optional.empty()));
                        removed = true;
                    }
                }
            }
        }

        // ── Сборка документа ───────────────────────────────────────

        private Attributes attributes(Merged m) {
            List<Attribute> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Attribute attribute : m.primary().attributes()) {
                seen.add(attribute.name());
                if (attribute.name().equals(ID)) {
                    list.add(attribute);
                } else {
                    m.attribute(attribute.name()).ifPresent(v -> list.add(new Attribute(attribute.name(), v)));
                }
            }
            for (Optional<Obj> other : List.of(m.ours(), m.theirs())) {
                other.ifPresent(o -> o.attributes().forEach(a -> {
                    if (seen.add(a.name())) {
                        m.attribute(a.name()).ifPresent(v -> list.add(new Attribute(a.name(), v)));
                    }
                }));
            }
            return new Attributes(list);
        }

        /** Содержимое объекта: дети-объекты, значения и фрагменты в слитом порядке. */
        private List<DocumentContent> content(Merged m, Optional<ArchiId> container, Set<ArchiId> present) {
            boolean view = m.kind() == Kind.VIEW;
            Side diagramSide = m.sideOf(ModelChanges.DIAGRAM);
            Map<ArchiId, DocumentNode> diagram = new LinkedHashMap<>();
            Set<Key> keys = new LinkedHashSet<>();
            if (view) {
                prune(m.id(), nodes(m.side(diagramSide).content()), present)
                        .forEach(node -> diagram.put(node.archiId(), node));
                diagram.keySet().forEach(id -> keys.add(new NodeKey(id)));
            } else {
                result.values().stream().filter(c -> c.kind() != Kind.MODEL && c.parent().equals(container))
                        .forEach(c -> keys.add(new NodeKey(c.id())));
            }
            Map<String, List<DocumentContent>> fieldItems = new LinkedHashMap<>();
            for (Optional<Obj> side : List.of(m.ours(), m.theirs())) {
                side.ifPresent(o -> o.content().forEach(item -> {
                    if (!(item instanceof DocumentNode)) {
                        fieldItems.putIfAbsent(field(item), List.of());
                    }
                }));
            }
            fieldItems.replaceAll((field, empty) -> items(m.side(m.sideOf(field)).content(), field));
            fieldItems.forEach((field, list) -> {
                for (int i = 0; i < list.size(); i++) {
                    keys.add(field.equals(ModelChanges.RAW_XML) ? new RawKey(i) : new ValueKey(field, i));
                }
            });
            Optional<List<Key>> forced = view ? Optional.of(sequence(m.side(diagramSide).content())) : Optional.empty();
            List<Key> order = order(sequence(base.find(m.id()).map(Obj::content).orElse(List.of())),
                    sequence(m.ours().map(Obj::content).orElse(List.of())),
                    sequence(m.theirs().map(Obj::content).orElse(List.of())), forced, keys);
            List<DocumentContent> content = new ArrayList<>();
            for (Key key : order) {
                DocumentContent item = switch (key) {
                    case NodeKey node -> view ? diagram.get(node.id())
                            : node(result.get(node.id()), present);
                    case ValueKey value -> fieldItems.get(value.field()).get(value.index());
                    case RawKey raw -> fieldItems.get(ModelChanges.RAW_XML).get(raw.index());
                };
                content.add(at(item, content.size()));
            }
            return content;
        }

        private DocumentNode node(Merged m, Set<ArchiId> present) {
            return new DocumentNode(m.primary().tag(), DocumentOrder.of(0), attributes(m),
                    content(m, Optional.of(m.id()), present));
        }

        /**
         * Узлы и рёбра представления, ссылающиеся на удалённое, уходят: узел без элемента,
         * связи или представления — с вложенными, ребро без связи или без конца.
         */
        private List<DocumentNode> prune(ArchiId view, List<DocumentNode> children, Set<ArchiId> present) {
            Map<ArchiId, DocumentNode> all = new LinkedHashMap<>();
            children.forEach(child -> descendants(child, all));
            Set<ArchiId> removed = new HashSet<>();
            for (DocumentNode node : all.values()) {
                if (removed.contains(node.archiId())) {
                    continue;
                }
                boolean dangling = Stream.of(ELEMENT_REF, RELATIONSHIP_REF, MODEL_REF)
                        .anyMatch(ref -> node.attribute(ref).filter(v -> !present.contains(ArchiId.of(v))).isPresent());
                if (dangling) {
                    removeSubtree(node, removed);
                    cascade.add(new Cascade(diagramKind(node), node.archiId(), diagramName(node),
                            Reason.DIAGRAM_REFERENCE_REMOVED, Optional.of(view)));
                }
            }
            boolean more = true;
            while (more) {
                more = false;
                for (DocumentNode node : all.values()) {
                    if (!node.tag().equals(CONNECTION_TAG) || removed.contains(node.archiId())) {
                        continue;
                    }
                    boolean dangling = Stream.of(SOURCE, TARGET).anyMatch(end -> node.attribute(end)
                            .map(ArchiId::of).filter(e -> all.containsKey(e) && !removed.contains(e)).isEmpty());
                    if (dangling) {
                        removeSubtree(node, removed);
                        cascade.add(new Cascade(CascadeKind.VIEW_CONNECTION, node.archiId(), diagramName(node),
                                Reason.CONNECTION_ENDPOINT_REMOVED, Optional.of(view)));
                        more = true;
                    }
                }
            }
            if (removed.isEmpty()) {
                return children;
            }
            List<DocumentNode> kept = new ArrayList<>();
            for (DocumentNode child : children) {
                if (!removed.contains(child.archiId())) {
                    kept.add(rebuild(child, removed));
                }
            }
            return kept;
        }

        private DocumentNode rebuild(DocumentNode node, Set<ArchiId> removed) {
            List<DocumentContent> content = new ArrayList<>();
            for (DocumentContent item : node.content()) {
                if (item instanceof DocumentNode child) {
                    if (removed.contains(child.archiId())) {
                        continue;
                    }
                    item = rebuild(child, removed);
                }
                content.add(at(item, content.size()));
            }
            List<Attribute> attributes = new ArrayList<>();
            for (Attribute attribute : node.attributes()) {
                if (attribute.name().equals(TARGET_CONNECTIONS)) {
                    String kept = Arrays.stream(attribute.value().split(" ")).filter(v -> !v.isBlank())
                            .filter(v -> !removed.contains(ArchiId.of(v))).collect(Collectors.joining(" "));
                    if (!kept.isEmpty()) {
                        attributes.add(new Attribute(attribute.name(), kept));
                    }
                } else {
                    attributes.add(attribute);
                }
            }
            return new DocumentNode(node.tag(), node.order(), new Attributes(attributes), content);
        }

        private String diagramName(DocumentNode node) {
            return node.attribute(NAME).or(() -> Stream.of(ELEMENT_REF, RELATIONSHIP_REF)
                    .map(node::attribute).flatMap(Optional::stream).findFirst()
                    .flatMap(ref -> ours.find(ArchiId.of(ref)).or(() -> theirs.find(ArchiId.of(ref))))
                    .map(Obj::name)).orElse("");
        }
    }

    // ── Порядок ────────────────────────────────────────────────────

    /**
     * Слитый порядок ключей: основа — ветвь, менявшая относительный порядок общих с базой
     * ключей (или заданная), остальное встаёт после всех соседей, стоявших перед ним в своей ветви.
     */
    private static List<Key> order(List<Key> base, List<Key> ours, List<Key> theirs, Optional<List<Key>> forced,
                                   Set<Key> keep) {
        List<Key> primary;
        List<Key> other;
        if (forced.isPresent()) {
            primary = forced.get();
            other = primary == ours ? theirs : ours;
        } else if (!reordered(base, ours) && reordered(base, theirs)) {
            primary = theirs;
            other = ours;
        } else {
            primary = ours;
            other = theirs;
        }
        List<Key> merged = new ArrayList<>(primary.stream().filter(keep::contains).distinct().toList());
        for (List<Key> source : List.of(other, ours, theirs, base)) {
            int anchor = -1;
            for (Key key : source) {
                int at = merged.indexOf(key);
                if (at >= 0) {
                    anchor = Math.max(anchor, at);
                } else if (keep.contains(key)) {
                    merged.add(anchor + 1, key);
                    anchor++;
                }
            }
        }
        keep.stream().filter(k -> !merged.contains(k)).forEach(merged::add);
        return merged;
    }

    private static boolean reordered(List<Key> base, List<Key> side) {
        Set<Key> common = new HashSet<>(base);
        common.retainAll(new HashSet<>(side));
        return !base.stream().filter(common::contains).toList().equals(side.stream().filter(common::contains).toList());
    }

    /** Ключи содержимого стороны по порядку файла. */
    private static List<Key> sequence(List<DocumentContent> content) {
        List<Key> keys = new ArrayList<>();
        Map<String, Integer> counters = new HashMap<>();
        for (DocumentContent item : content) {
            switch (item) {
                case DocumentNode node -> keys.add(new NodeKey(node.archiId()));
                case DocumentValue value -> {
                    String field = field(value);
                    keys.add(new ValueKey(field, counters.merge(field, 1, Integer::sum) - 1));
                }
                case RawXmlFragment raw -> keys.add(new RawKey(counters.merge(ModelChanges.RAW_XML, 1,
                        Integer::sum) - 1));
            }
        }
        return keys;
    }

    // ── Поля ───────────────────────────────────────────────────────

    /** Поля объекта для сравнения: атрибуты, значения по полю, фрагменты, папка, диаграмма. */
    private static Map<String, Object> fields(Obj obj) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Attribute attribute : obj.attributes()) {
            if (!attribute.name().equals(ID)) {
                fields.put(ATTRIBUTE + attribute.name(), attribute.value());
            }
        }
        for (DocumentContent item : obj.content()) {
            if (!(item instanceof DocumentNode)) {
                fields.computeIfAbsent(field(item), f -> shapes(items(obj.content(), f)));
            }
        }
        if (obj.kind() != Kind.MODEL) {
            fields.put(ModelChanges.FOLDER, obj.parent());
        }
        if (obj.kind() == Kind.VIEW) {
            fields.put(ModelChanges.DIAGRAM, shapes(List.copyOf(nodes(obj.content()))));
        }
        return fields;
    }

    private static String field(DocumentContent item) {
        return switch (item) {
            case DocumentValue value -> switch (value.tag()) {
                case "documentation", "purpose" -> ModelChanges.DOCUMENTATION;
                case "property" -> ModelChanges.PROPERTIES;
                default -> value.tag();
            };
            case RawXmlFragment raw -> ModelChanges.RAW_XML;
            case DocumentNode node -> throw new IllegalArgumentException(node.tag());
        };
    }

    private static List<DocumentContent> items(List<DocumentContent> content, String field) {
        return content.stream().filter(item -> !(item instanceof DocumentNode) && field(item).equals(field)).toList();
    }

    private static List<DocumentValue> values(List<DocumentContent> content, String field) {
        return items(content, field).stream().filter(DocumentValue.class::isInstance).map(DocumentValue.class::cast)
                .toList();
    }

    private static List<RawXmlFragment> raws(List<DocumentContent> content) {
        return content.stream().filter(RawXmlFragment.class::isInstance).map(RawXmlFragment.class::cast).toList();
    }

    private static List<DocumentNode> nodes(List<DocumentContent> content) {
        return content.stream().filter(DocumentNode.class::isInstance).map(DocumentNode.class::cast).toList();
    }

    private static List<Shape> shapes(List<? extends DocumentContent> content) {
        return content.stream().map(EntityMerge::shape).toList();
    }

    private static Shape shape(DocumentContent item) {
        return switch (item) {
            case DocumentNode node -> new Shape(node.tag(), node.attributes().list(), Optional.empty(),
                    shapes(node.content()));
            case DocumentValue value -> new Shape(value.tag(), value.attributes().list(), value.text(), List.of());
            case RawXmlFragment raw -> new Shape("", List.of(), Optional.of(raw.xml()), List.of());
        };
    }

    private static int diagramObjects(List<DocumentNode> nodes) {
        int count = 0;
        for (DocumentNode node : nodes) {
            count += 1 + diagramObjects(node.children());
        }
        return count;
    }

    private static void descendants(DocumentNode node, Map<ArchiId, DocumentNode> into) {
        into.put(node.archiId(), node);
        node.children().forEach(child -> descendants(child, into));
    }

    private static void removeSubtree(DocumentNode node, Set<ArchiId> removed) {
        removed.add(node.archiId());
        node.children().forEach(child -> removeSubtree(child, removed));
    }

    private static DocumentContent at(DocumentContent item, int order) {
        DocumentOrder position = DocumentOrder.of(order);
        return switch (item) {
            case DocumentNode node -> new DocumentNode(node.tag(), position, node.attributes(), node.content());
            case DocumentValue value -> new DocumentValue(value.tag(), position, value.attributes(), value.text());
            case RawXmlFragment raw -> new RawXmlFragment(raw.xml(), raw.parentArchiId(), position);
        };
    }

    private static String conflictId(ArchiId id, String field) {
        return id.value() + ":" + field;
    }

    /** Имя поля в отчёте: атрибут — своим именем, {@code xsi:type} — {@code type}. */
    private static String display(String field) {
        if (!field.startsWith(ATTRIBUTE)) {
            return field;
        }
        String name = field.substring(ATTRIBUTE.length());
        return name.equals(XSI_TYPE) ? ModelChanges.TYPE : name;
    }

    private static CascadeKind cascadeKind(Kind kind) {
        return switch (kind) {
            case FOLDER -> CascadeKind.FOLDER;
            case ELEMENT -> CascadeKind.ELEMENT;
            case RELATIONSHIP -> CascadeKind.RELATIONSHIP;
            case VIEW -> CascadeKind.VIEW;
            case MODEL -> throw new IllegalArgumentException(kind.name());
        };
    }

    private static CascadeKind diagramKind(DocumentNode node) {
        return node.tag().equals(CONNECTION_TAG) ? CascadeKind.VIEW_CONNECTION : CascadeKind.VIEW_NODE;
    }

    private static String bareType(String xsiType) {
        return xsiType.startsWith("archimate:") ? xsiType.substring("archimate:".length()) : xsiType;
    }
}
