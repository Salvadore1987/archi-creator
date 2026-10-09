package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.TrackedMap;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelDeleted;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptDefinition;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationMatrix;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Архитектурная модель — агрегат.
 *
 * <p>Граница согласованности: уникальность {@code archiId}, концы связей, порядок в папке,
 * дерево папок и жизненный цикл проверяются в пределах одного экземпляра. Представления — отдельный
 * агрегат; модель знает о них лишь {@link ViewRef}: где лежат и какое место занимают.
 *
 * <p>Изменения копятся в {@link TrackedMap}: хранилище пишет только тронутое.
 */
public final class ArchitectureModel {

    private static final ArchiTypeRegistry REGISTRY = ArchiTypeRegistry.archimate32();

    private final ModelId id;
    private final WorkspaceId workspaceId;
    private final ArchiId archiId;
    private String name;
    private Optional<String> documentation;
    private final String archiVersion;
    private ModelStatus status;
    private List<PropertyEntry> properties;
    private final Optional<RawXml> rawXml;
    private final String createdBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    private final TrackedMap<FolderId, ModelFolder> folders = new TrackedMap<>();
    private final TrackedMap<ElementId, Element> elements = new TrackedMap<>();
    private final TrackedMap<RelationshipId, Relationship> relationships = new TrackedMap<>();
    private final Map<ViewId, ViewRef> views = new LinkedHashMap<>();

    private final boolean fresh;
    private boolean headerChanged;
    private final List<DomainEvent> events = new ArrayList<>();

    private ArchitectureModel(ModelHeader header, boolean fresh) {
        this.id = header.id();
        this.workspaceId = header.workspaceId();
        this.archiId = header.archiId();
        this.name = header.name();
        this.documentation = header.documentation();
        this.archiVersion = header.archiVersion();
        this.status = header.status();
        this.properties = header.properties();
        this.rawXml = header.rawXml();
        this.createdBy = header.createdBy();
        this.createdAt = header.createdAt();
        this.updatedAt = header.updatedAt();
        this.version = header.version();
        this.fresh = fresh;
    }

    // ── Создание и загрузка ─────────────────────────────────────────

    /** Пустая модель с девятью корневыми папками. */
    public static ArchitectureModel create(ModelId id, WorkspaceId workspaceId, ArchiId archiId, String name,
                                           EditorIdentity author, Instant now, Supplier<FolderId> folderIds,
                                           Supplier<ArchiId> archiIds) {
        ModelHeader header = new ModelHeader(id, workspaceId, archiId, Names.required(name, Names.MODEL),
                Optional.empty(), ModelHeader.DEFAULT_ARCHI_VERSION, ModelStatus.ACTIVE, List.of(),
                Optional.empty(), author.subject(), now, now, 0);
        ArchitectureModel model = new ArchitectureModel(header, true);
        FolderTreeInitializer.rootFolders(folderIds, archiIds).forEach(f -> model.folders.put(f.id(), f));
        return model;
    }

    /**
     * Модель из импортированного документа: всё содержимое новое. Структура проверяется
     * так же, как при правке, кроме матрицы связей — нарушения импорта сообщаются,
     * а не отклоняются. Недостающие корневые папки дописываются,
     * как это делает Archi при открытии такого файла.
     */
    public static ArchitectureModel imported(ModelHeader header, List<ModelFolder> folders, List<Element> elements,
                                             List<Relationship> relationships, List<ViewRef> views,
                                             Supplier<FolderId> folderIds, Supplier<ArchiId> archiIds) {
        ArchitectureModel model = new ArchitectureModel(header, true);
        folders.forEach(f -> model.folders.put(f.id(), f));
        elements.forEach(e -> model.elements.put(e.id(), e));
        relationships.forEach(r -> model.relationships.put(r.id(), r));
        views.forEach(v -> model.views.put(v.id(), v));
        model.addMissingRoots(folderIds, archiIds);
        model.verifyStructure();
        return model;
    }

    /** Загрузка из хранилища: изменением не считается. */
    public static ArchitectureModel restore(ModelHeader header, Collection<ModelFolder> folders,
                                            Collection<Element> elements, Collection<Relationship> relationships,
                                            Collection<ViewRef> views) {
        ArchitectureModel model = new ArchitectureModel(header, false);
        folders.forEach(f -> model.folders.load(f.id(), f));
        elements.forEach(e -> model.elements.load(e.id(), e));
        relationships.forEach(r -> model.relationships.load(r.id(), r));
        views.forEach(v -> model.views.put(v.id(), v));
        return model;
    }

    private void addMissingRoots(Supplier<FolderId> folderIds, Supplier<ArchiId> archiIds) {
        Map<FolderType, ModelFolder> roots = roots();
        for (FolderType type : FolderType.values()) {
            if (!roots.containsKey(type)) {
                SortOrder order = SortOrder.afterLast(folders.values().stream()
                        .filter(ModelFolder::root).map(ModelFolder::sortOrder).toList());
                ModelFolder root = FolderTreeInitializer.root(type, folderIds.get(),
                        archiIds.get(), order);
                folders.put(root.id(), root);
            }
        }
    }

    /** Уникальность {@code archiId}, концы связей и дерево папок — над всем содержимым разом. */
    private void verifyStructure() {
        Set<ArchiId> seen = new HashSet<>();
        Stream.of(folders.values().stream().map(ModelFolder::archiId),
                        elements.values().stream().map(Element::archiId),
                        relationships.values().stream().map(Relationship::archiId),
                        views.values().stream().map(ViewRef::archiId))
                .flatMap(s -> s)
                .forEach(archi -> {
                    if (!seen.add(archi)) {
                        throw new ModelingException(ModelingCodes.ARCHI_ID_UNIQUE, Failure.UNPROCESSABLE,
                                Message.of(ModelingMessages.ARCHI_ID_REPEATED, archi));
                    }
                });
        Map<FolderType, Integer> rootCount = new EnumMap<>(FolderType.class);
        for (ModelFolder folder : folders.values()) {
            folder.folderType().ifPresent(t -> rootCount.merge(t, 1, Integer::sum));
            folder.parentId().ifPresent(parent -> {
                requireFolder(parent);
                rootOf(folder.id());
            });
        }
        rootCount.forEach((type, count) -> {
            if (count > 1) {
                throw new ModelingException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                        Message.of(ModelingMessages.ROOT_FOLDER_REPEATED, type.fileValue(), count));
            }
        });
        elements.values().forEach(e -> requireFolder(e.folderId()));
        views.values().forEach(v -> requireFolder(v.folderId()));
        for (Relationship relationship : relationships.values()) {
            requireFolder(relationship.folderId());
            requireConcept(relationship.source());
            requireConcept(relationship.target());
        }
    }

    // ── Жизненный цикл ──────────────────────────────────────────────

    public void delete(EditorIdentity by, Instant now) {
        transition(ModelStatus.ACTIVE, ModelStatus.DELETED, "DeleteModel", now);
        events.add(new ModelDeleted(id, workspaceId, by.subject(), now));
    }

    public void restoreDeleted(Instant now) {
        transition(ModelStatus.DELETED, ModelStatus.ACTIVE, "RestoreModel", now);
    }

    /** Только из {@code DELETED}: уничтожение активной модели одной командой запрещено. */
    public void purge(Instant now) {
        transition(ModelStatus.DELETED, ModelStatus.PURGED, "PurgeModel", now);
    }

    private void transition(ModelStatus from, ModelStatus to, String command, Instant now) {
        if (status != from) {
            throw new ModelingException(ModelingCodes.LIFECYCLE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.TRANSITION_NOT_ALLOWED, command, status, to));
        }
        status = to;
        touch(now);
    }

    /** В {@code DELETED} любая команда изменения содержимого отклоняется. */
    public void requireActive() {
        if (status != ModelStatus.ACTIVE) {
            throw new ModelingException(ModelingCodes.LIFECYCLE, Failure.CONFLICT,
                    Message.of(ModelingMessages.MODEL_NOT_ACTIVE, status));
        }
    }

    public void touch(Instant now) {
        updatedAt = now;
        headerChanged = true;
    }

    // ── Собственные поля ────────────────────────────────────────────

    public void rename(String newName, Instant now) {
        requireActive();
        name = Names.required(newName, Names.MODEL);
        touch(now);
    }

    public void changeDocumentation(Optional<String> newDocumentation, Instant now) {
        requireActive();
        documentation = newDocumentation.filter(d -> !d.isEmpty());
        touch(now);
    }

    // ── Папки ───────────────────────────────────────────────────────

    public ModelFolder createFolder(FolderId parentId, String folderName, FolderId newId, ArchiId newArchiId,
                                    Instant now) {
        requireActive();
        requireFolder(parentId);
        requireFreeArchiId(newArchiId);
        ModelFolder folder = new ModelFolder(newId, Optional.of(parentId), newArchiId,
                Names.required(folderName, Names.FOLDER), Optional.empty(), nextOrderIn(parentId), Optional.empty());
        folders.put(folder.id(), folder);
        touch(now);
        return folder;
    }

    public ModelFolder renameFolder(FolderId folderId, String newName, Instant now) {
        requireActive();
        ModelFolder renamed = requireFolder(folderId).withName(Names.required(newName, Names.FOLDER));
        folders.put(folderId, renamed);
        touch(now);
        return renamed;
    }

    /** Корни неудаляемы; непустая папка не удаляется — содержимое удаляется или переносится явно. */
    public void removeFolder(FolderId folderId, Instant now) {
        requireActive();
        ModelFolder folder = requireFolder(folderId);
        if (folder.root()) {
            throw new ModelingException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.ROOT_FOLDER_UNDELETABLE, folder.name()));
        }
        if (!childOrders(folderId).isEmpty()) {
            throw new ModelingException(ModelingCodes.FOLDER_NOT_EMPTY, Failure.CONFLICT,
                    Message.of(ModelingMessages.FOLDER_NOT_EMPTY, folder.name()));
        }
        folders.remove(folderId);
        touch(now);
    }

    /**
     * Перенос в папку. Объекты остаются в поддереве своего корня — элементы своего слоя,
     * связи в {@code relations}, представления в {@code diagrams}; папка не переносится
     * в собственного потомка. Соседи в новой папке не перенумеровываются.
     * Нарушение у любого из объектов отклоняет перенос целиком.
     *
     * @return новое место перенесённых представлений — их агрегаты правит вызывающий
     */
    public List<ViewRef> moveToFolder(FolderId targetId, List<UUID> itemIds, Instant now) {
        requireActive();
        FolderType targetRoot = rootOf(requireFolder(targetId)).folderType().orElseThrow();
        List<ViewRef> movedViews = new ArrayList<>();
        for (UUID itemId : itemIds) {
            FolderId asFolder = FolderId.of(itemId);
            ElementId asElement = ElementId.of(itemId);
            RelationshipId asRelationship = RelationshipId.of(itemId);
            ViewId asView = ViewId.of(itemId);
            if (folders.contains(asFolder)) {
                moveFolder(asFolder, targetId, targetRoot);
            } else if (elements.contains(asElement)) {
                Element element = elements.get(asElement).orElseThrow();
                requireSameRoot(element.folderId(), targetRoot, Message.of(ModelingMessages.ELEMENT, element.name()));
                elements.put(asElement, element.movedTo(targetId, nextOrderIn(targetId)));
            } else if (relationships.contains(asRelationship)) {
                Relationship relationship = relationships.get(asRelationship).orElseThrow();
                requireSameRoot(relationship.folderId(), targetRoot,
                        Message.of(ModelingMessages.RELATIONSHIP, relationship.archiId()));
                relationships.put(asRelationship, relationship.movedTo(targetId, nextOrderIn(targetId)));
            } else if (views.containsKey(asView)) {
                ViewRef view = views.get(asView);
                requireSameRoot(view.folderId(), targetRoot, Message.of(ModelingMessages.VIEW, view.name()));
                ViewRef moved = new ViewRef(view.id(), targetId, view.archiId(), view.archiType(), view.name(),
                        nextOrderIn(targetId));
                views.put(asView, moved);
                movedViews.add(moved);
            } else {
                throw ModelingException.notFound(Message.of(ModelingMessages.OBJECT_IN_MODEL, itemId));
            }
        }
        touch(now);
        return movedViews;
    }

    private void moveFolder(FolderId folderId, FolderId targetId, FolderType targetRoot) {
        ModelFolder folder = requireFolder(folderId);
        if (folder.root()) {
            throw new ModelingException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.ROOT_FOLDER_UNMOVABLE, folder.name()));
        }
        if (isWithin(targetId, folderId)) {
            throw new ModelingException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.FOLDER_INTO_ITSELF, folder.name()));
        }
        requireSameRoot(folderId, targetRoot, Message.of(ModelingMessages.FOLDER, folder.name()));
        folders.put(folderId, folder.movedTo(targetId, nextOrderIn(targetId)));
    }

    private void requireSameRoot(FolderId current, FolderType targetRoot, Message what) {
        FolderType currentRoot = rootOf(requireFolder(current)).folderType().orElseThrow();
        if (currentRoot != targetRoot) {
            throw new ModelingException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.WRONG_ROOT, what, currentRoot.fileValue(), targetRoot.fileValue()));
        }
    }

    // ── Элементы ────────────────────────────────────────────────────

    /**
     * Новый элемент в папке своего слоя. Тип — поддержанный фазой 1: тип фазы 2
     * и 3 создать нельзя, импортированный остаётся opaque.
     */
    public Element addElement(ArchiType archiType, String elementName, FolderId folderId, ElementId newId,
                              ArchiId newArchiId, Instant now) {
        requireActive();
        ConceptDefinition concept = REGISTRY.find(archiType)
                .filter(c -> c.kind() == ConceptKind.ELEMENT || c.kind() == ConceptKind.JUNCTION)
                .orElseThrow(() -> ModelingException.invalid(Message.of(ModelingMessages.NOT_ELEMENT_TYPE, archiType)));
        if (!concept.supported()) {
            throw new ModelingException(ModelingCodes.TYPE_NOT_EDITABLE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.TYPE_NOT_EDITABLE, archiType));
        }
        requireSameRoot(folderId, FolderType.forLayer(concept.layer()),
                Message.of(ModelingMessages.ELEMENT_OF_TYPE, archiType.simpleName()));
        requireFreeArchiId(newArchiId);
        Element element = new Element(newId, folderId, newArchiId, archiType, Names.limited(elementName, Names.ELEMENT),
                Optional.empty(), List.of(), nextOrderIn(folderId), true, Optional.empty());
        elements.put(element.id(), element);
        touch(now);
        return element;
    }

    public Element updateElement(ElementId elementId, String newName, Optional<String> newDocumentation,
                                 List<PropertyEntry> newProperties, Instant now) {
        requireActive();
        Element element = requireElement(elementId);
        requireEditable(element.supported(), element.archiType());
        Element edited = element.edited(Names.limited(newName, Names.ELEMENT),
                newDocumentation.filter(d -> !d.isEmpty()), newProperties);
        elements.put(elementId, edited);
        touch(now);
        return edited;
    }

    /**
     * Элемент со связями не удаляется: сначала удаляются связи, явно, как в Archi.
     * Размещения на представлениях снимает вызывающий.
     */
    public Element removeElement(ElementId elementId, Instant now) {
        requireActive();
        Element element = requireElement(elementId);
        requireNoRelationshipsTouching(elementId, Message.of(ModelingMessages.ELEMENT, element.name()));
        elements.remove(elementId);
        touch(now);
        return element;
    }

    // ── Связи ───────────────────────────────────────────────────────

    /**
     * Новая связь в папке {@code Relations}. Тип проверяется по матрице ArchiMate 3.2;
     * дубль того же типа между той же парой не создаётся — возвращается
     * существующая связь.
     */
    public Created<Relationship> addRelationship(ArchiType archiType, ConceptRef source, ConceptRef target,
                                                 Optional<String> relationshipName, RelationshipId newId,
                                                 ArchiId newArchiId, Instant now) {
        return addRelationship(archiType, source, target, relationshipName, Optional.empty(), Optional.empty(),
                Optional.empty(), newId, newArchiId, now);
    }

    /**
     * То же с местом в дереве и атрибутами типа. Папка — в поддереве {@code Relations},
     * без неё связь ложится в корень; вид доступа бывает только у Access, направленность —
     * только у Association.
     */
    public Created<Relationship> addRelationship(ArchiType archiType, ConceptRef source, ConceptRef target,
                                                 Optional<String> relationshipName, Optional<FolderId> folderId,
                                                 Optional<AccessType> accessType, Optional<Boolean> directed,
                                                 RelationshipId newId, ArchiId newArchiId, Instant now) {
        requireActive();
        RelationshipType type = RelationshipType.fromArchiType(archiType)
                .orElseThrow(() -> ModelingException.invalid(Message.of(ModelingMessages.NOT_RELATIONSHIP_TYPE, archiType)));
        if (accessType.isPresent() && type != RelationshipType.ACCESS) {
            throw ModelingException.invalid(Message.of(ModelingMessages.ACCESS_TYPE_MISPLACED, archiType));
        }
        if (directed.isPresent() && type != RelationshipType.ASSOCIATION) {
            throw ModelingException.invalid(Message.of(ModelingMessages.DIRECTED_MISPLACED, archiType));
        }
        ArchiType sourceType = conceptType(source);
        ArchiType targetType = conceptType(target);
        Optional<Relationship> duplicate = relationships.values().stream()
                .filter(r -> r.archiType().equals(archiType) && r.source().equals(source) && r.target().equals(target))
                .findFirst();
        if (duplicate.isPresent()) {
            return new Created<>(duplicate.get(), false);
        }
        RelationMatrix.archimate32().requirePermitted(sourceType, targetType, type);
        FolderId folder = folderId.orElseGet(() -> roots().get(FolderType.RELATIONS).id());
        requireSameRoot(folder, FolderType.RELATIONS,
                Message.of(ModelingMessages.RELATIONSHIP_OF_TYPE, archiType.simpleName()));
        requireFreeArchiId(newArchiId);
        Relationship relationship = new Relationship(newId, folder, newArchiId, archiType, source, target,
                relationshipName.filter(n -> !n.isEmpty()), Optional.empty(), accessType, directed,
                List.of(), nextOrderIn(folder), true, Optional.empty());
        relationships.put(relationship.id(), relationship);
        touch(now);
        return new Created<>(relationship, true);
    }

    public Relationship updateRelationship(RelationshipId relationshipId, Optional<String> newName,
                                           Optional<String> newDocumentation, List<PropertyEntry> newProperties,
                                           Instant now) {
        requireActive();
        Relationship relationship = requireRelationship(relationshipId);
        requireEditable(relationship.supported(), relationship.archiType());
        Relationship edited = relationship.edited(newName.filter(n -> !n.isEmpty()).map(n -> Names.limited(n, Names.RELATIONSHIP)),
                newDocumentation.filter(d -> !d.isEmpty()), newProperties);
        relationships.put(relationshipId, edited);
        touch(now);
        return edited;
    }

    /** Связь, к которой примыкает другая связь, не удаляется. */
    public Relationship removeRelationship(RelationshipId relationshipId, Instant now) {
        requireActive();
        Relationship relationship = requireRelationship(relationshipId);
        requireNoRelationshipsTouching(relationshipId,
                Message.of(ModelingMessages.RELATIONSHIP, relationship.archiId()));
        relationships.remove(relationshipId);
        touch(now);
        return relationship;
    }

    private void requireNoRelationshipsTouching(ConceptRef concept, Message what) {
        List<Relationship> touching = relationships.values().stream().filter(r -> r.touches(concept)).toList();
        if (!touching.isEmpty()) {
            throw new ModelingException(ModelingCodes.RELATIONSHIP_ENDS, Failure.CONFLICT,
                    Message.of(ModelingMessages.HAS_RELATIONSHIPS, what, touching.size()),
                    Map.of("relationships", touching.stream().map(r -> r.id().toString()).toList()));
        }
    }

    // ── Представления в дереве ──────────────────────────────────────

    /** Место для нового представления: папка в поддереве {@code diagrams}. */
    public ViewRef placeNewView(ViewId viewId, DiagramType archiType, String viewName, FolderId folderId,
                                ArchiId newArchiId, Instant now) {
        requireActive();
        requireSameRoot(folderId, FolderType.DIAGRAMS, Message.of(ModelingMessages.VIEW, viewName));
        requireFreeArchiId(newArchiId);
        ViewRef ref = new ViewRef(viewId, folderId, newArchiId, archiType, Names.limited(viewName, Names.VIEW),
                nextOrderIn(folderId));
        views.put(viewId, ref);
        touch(now);
        return ref;
    }

    public void forgetView(ViewId viewId, Instant now) {
        requireActive();
        views.remove(viewId);
        touch(now);
    }

    public void renameView(ViewId viewId, String newName) {
        ViewRef view = views.get(viewId);
        if (view != null) {
            views.put(viewId, new ViewRef(view.id(), view.folderId(), view.archiId(), view.archiType(), newName,
                    view.sortOrder()));
        }
    }

    // ── Проверки и выборки ──────────────────────────────────────────

    public ModelFolder requireFolder(FolderId folderId) {
        return folders.get(folderId)
                .orElseThrow(() -> new ModelingException(ModelingCodes.NOT_FOUND, Failure.UNPROCESSABLE,
                        Message.of(ModelingMessages.FOLDER_OF_OTHER_MODEL, folderId, id)));
    }

    public Element requireElement(ElementId elementId) {
        return elements.get(elementId)
                .orElseThrow(() -> ModelingException.notFound(Message.of(ModelingMessages.ELEMENT, elementId)));
    }

    public Relationship requireRelationship(RelationshipId relationshipId) {
        return relationships.get(relationshipId)
                .orElseThrow(() -> ModelingException.notFound(Message.of(ModelingMessages.RELATIONSHIP, relationshipId)));
    }

    /** Конец связи обязан принадлежать этой модели. */
    public ArchiType conceptType(ConceptRef concept) {
        requireConcept(concept);
        return switch (concept) {
            case ElementId e -> elements.get(e).orElseThrow().archiType();
            case RelationshipId r -> relationships.get(r).orElseThrow().archiType();
        };
    }

    private void requireConcept(ConceptRef concept) {
        boolean present = switch (concept) {
            case ElementId e -> elements.contains(e);
            case RelationshipId r -> relationships.contains(r);
        };
        if (!present) {
            throw new ModelingException(ModelingCodes.RELATIONSHIP_ENDS, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.END_OF_OTHER_MODEL, concept.value(), id));
        }
    }

    private static void requireEditable(boolean supported, ArchiType archiType) {
        if (!supported) {
            throw new ModelingException(ModelingCodes.TYPE_NOT_EDITABLE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.OPAQUE_NOT_EDITABLE, archiType));
        }
    }

    /** {@code archiId} не повторяется в модели. */
    public void requireFreeArchiId(ArchiId candidate) {
        if (archiIdTaken(candidate)) {
            throw new ModelingException(ModelingCodes.ARCHI_ID_UNIQUE, Failure.CONFLICT,
                    Message.of(ModelingMessages.ARCHI_ID_TAKEN, candidate, id));
        }
    }

    public boolean archiIdTaken(ArchiId candidate) {
        return candidate.equals(archiId)
                || folders.values().stream().anyMatch(f -> f.archiId().equals(candidate))
                || elements.values().stream().anyMatch(e -> e.archiId().equals(candidate))
                || relationships.values().stream().anyMatch(r -> r.archiId().equals(candidate))
                || views.values().stream().anyMatch(v -> v.archiId().equals(candidate));
    }

    /** Корневые папки по типу. */
    public Map<FolderType, ModelFolder> roots() {
        Map<FolderType, ModelFolder> roots = new EnumMap<>(FolderType.class);
        folders.values().stream().filter(ModelFolder::root).forEach(f -> roots.put(f.folderType().orElseThrow(), f));
        return roots;
    }

    /** Корень поддерева, в котором лежит папка; цикл в дереве — нарушение структуры папок. */
    public ModelFolder rootOf(FolderId folderId) {
        return rootOf(requireFolder(folderId));
    }

    private ModelFolder rootOf(ModelFolder folder) {
        ModelFolder current = folder;
        Set<FolderId> visited = new HashSet<>();
        while (current.parentId().isPresent()) {
            if (!visited.add(current.id())) {
                throw new ModelingException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                        Message.of(ModelingMessages.FOLDER_CYCLE, current.archiId()));
            }
            current = requireFolder(current.parentId().get());
        }
        return current;
    }

    /** Лежит ли {@code candidate} в поддереве {@code ancestor}, включая его самого. */
    private boolean isWithin(FolderId candidate, FolderId ancestor) {
        Optional<FolderId> current = Optional.of(candidate);
        while (current.isPresent()) {
            if (current.get().equals(ancestor)) {
                return true;
            }
            current = requireFolder(current.get()).parentId();
        }
        return false;
    }

    /** Позиции всех, кто лежит в папке: подпапки, элементы, связи, представления. */
    private List<SortOrder> childOrders(FolderId folderId) {
        List<SortOrder> orders = new ArrayList<>();
        folders.values().stream().filter(f -> f.parentId().equals(Optional.of(folderId)))
                .forEach(f -> orders.add(f.sortOrder()));
        elements.values().stream().filter(e -> e.folderId().equals(folderId)).forEach(e -> orders.add(e.sortOrder()));
        relationships.values().stream().filter(r -> r.folderId().equals(folderId))
                .forEach(r -> orders.add(r.sortOrder()));
        views.values().stream().filter(v -> v.folderId().equals(folderId)).forEach(v -> orders.add(v.sortOrder()));
        return orders;
    }

    /** Конец папки: соседи не перенумеровываются. */
    private SortOrder nextOrderIn(FolderId folderId) {
        return SortOrder.afterLast(childOrders(folderId));
    }

    // ── Состояние для хранилища и чтения ────────────────────────────

    public ModelHeader header() {
        return new ModelHeader(id, workspaceId, archiId, name, documentation, archiVersion, status, properties,
                rawXml, createdBy, createdAt, updatedAt, version);
    }

    public ModelId id() {
        return id;
    }

    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public ArchiId archiId() {
        return archiId;
    }

    public String name() {
        return name;
    }

    public ModelStatus status() {
        return status;
    }

    public String createdBy() {
        return createdBy;
    }

    public long version() {
        return version;
    }

    /** Ещё не записана в хранилище: создана или импортирована в этой транзакции. */
    public boolean fresh() {
        return fresh;
    }

    public boolean headerChanged() {
        return fresh || headerChanged;
    }

    public TrackedMap<FolderId, ModelFolder> folders() {
        return folders;
    }

    public TrackedMap<ElementId, Element> elements() {
        return elements;
    }

    public TrackedMap<RelationshipId, Relationship> relationships() {
        return relationships;
    }

    public Collection<ViewRef> views() {
        return List.copyOf(views.values());
    }

    public Optional<ViewRef> view(ViewId viewId) {
        return Optional.ofNullable(views.get(viewId));
    }

    /** События с последнего вызова; публикует слой приложения после коммита. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    /** Результат команды, которая может вернуть уже существующий объект вместо нового. */
    public record Created<T>(T value, boolean created) {

        public Created {
            Objects.requireNonNull(value, "value");
        }
    }
}
