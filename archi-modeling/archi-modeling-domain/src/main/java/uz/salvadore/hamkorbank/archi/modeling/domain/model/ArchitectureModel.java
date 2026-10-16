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
import uz.salvadore.hamkorbank.archi.modeling.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.TrackedEntities;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.TrackedMap;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.XmlText;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelDeletedEvent;
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
public final class ArchitectureModel extends AggregateRoot<ModelId> {

    private static final ArchiTypeRegistry REGISTRY = ArchiTypeRegistry.archimate32();

    private final WorkspaceId workspaceId;
    private final ArchiId archiId;
    private String name;
    private String documentation;
    private final String archiVersion;
    private ModelStatus status;
    private List<PropertyEntry> properties;
    private final RawXml rawXml;
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
        super(header.id());
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
        XmlText.require(name, Message.of(ModelingMessages.FIELD_NAME));
        ModelHeader header = new ModelHeader(id, workspaceId, archiId, Names.required(name, Names.MODEL),
                null, ModelHeader.DEFAULT_ARCHI_VERSION, ModelStatus.ACTIVE, List.of(),
                null, author.subject(), now, now, 0);
        ArchitectureModel model = new ArchitectureModel(header, true);
        FolderTreeInitializer.rootFolders(folderIds, archiIds).forEach(f -> model.folders.put(f.id(), f));
        return model;
    }

    /**
     * Модель из импортированного документа: всё содержимое новое. Проверяется целостность
     * файла: {@code archiId} уникален вместе с идентификатором самой модели, папки, элементы,
     * связи и представления лежат в существующих папках, дерево папок без циклов и с одним
     * корнем каждого вида, концы связей есть в модели. Правила правки — корень папки по слою,
     * поддержка типа, матрица связей — не применяются: файл Archi их может нарушать, нарушения
     * сообщает проверка модели. Недостающие корневые папки дописываются, как это делает Archi
     * при открытии такого файла.
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
        seen.add(archiId);
        Stream.of(folders.values().stream().map(ModelFolder::archiId),
                        elements.values().stream().map(Element::archiId),
                        relationships.values().stream().map(Relationship::archiId),
                        views.values().stream().map(ViewRef::archiId))
                .flatMap(s -> s)
                .forEach(archi -> {
                    if (!seen.add(archi)) {
                        throw new ModelingDomainException(ModelingCodes.ARCHI_ID_UNIQUE, Failure.UNPROCESSABLE,
                                Message.of(ModelingMessages.ARCHI_ID_REPEATED, archi));
                    }
                });
        Map<FolderType, Integer> rootCount = new EnumMap<>(FolderType.class);
        for (ModelFolder folder : folders.values()) {
            if (folder.folderType() != null) {
                rootCount.merge(folder.folderType(), 1, Integer::sum);
            }
            if (folder.parentId() != null) {
                requireFolder(folder.parentId());
                rootOf(folder.id());
            }
        }
        rootCount.forEach((type, count) -> {
            if (count > 1) {
                throw new ModelingDomainException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
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

    /** Перевод активной модели в удалённые; событие об удалении публикуется после коммита. */
    public void delete(EditorIdentity by, Instant now) {
        transition(ModelStatus.ACTIVE, ModelStatus.DELETED, Message.of(ModelingMessages.COMMAND_DELETE_MODEL), now);
        events.add(new ModelDeletedEvent(id(), workspaceId, by.subject(), now));
    }

    /** Возврат удалённой модели в активные. */
    public void restoreDeleted(Instant now) {
        transition(ModelStatus.DELETED, ModelStatus.ACTIVE, Message.of(ModelingMessages.COMMAND_RESTORE_MODEL), now);
    }

    /** Только из {@code DELETED}: уничтожение активной модели одной командой запрещено. */
    public void purge(Instant now) {
        transition(ModelStatus.DELETED, ModelStatus.PURGED, Message.of(ModelingMessages.COMMAND_PURGE_MODEL), now);
    }

    private void transition(ModelStatus from, ModelStatus to, Message command, Instant now) {
        if (status != from) {
            throw new ModelingDomainException(ModelingCodes.LIFECYCLE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.TRANSITION_NOT_ALLOWED, command, status.label(), to.label()));
        }
        status = to;
        touch(now);
    }

    /** В {@code DELETED} любая команда изменения содержимого отклоняется. */
    public void requireActive() {
        if (status != ModelStatus.ACTIVE) {
            throw new ModelingDomainException(ModelingCodes.LIFECYCLE, Failure.CONFLICT,
                    Message.of(ModelingMessages.MODEL_NOT_ACTIVE, status.label()));
        }
    }

    /** Отметка, что заголовок модели изменён: время правки и запись строки модели. */
    private void touch(Instant now) {
        updatedAt = now;
        headerChanged = true;
    }

    // ── Собственные поля ────────────────────────────────────────────

    /** Новое имя модели; пустое отклоняется. */
    public void rename(String newName, Instant now) {
        XmlText.require(newName, Message.of(ModelingMessages.FIELD_NAME));
        requireActive();
        name = Names.required(newName, Names.MODEL);
        touch(now);
    }

    /** Новая документация модели; {@code null} и пустая строка — документации нет. */
    public void changeDocumentation(String newDocumentation, Instant now) {
        XmlText.require(newDocumentation, Message.of(ModelingMessages.FIELD_DOCUMENTATION));
        requireActive();
        documentation = nonEmpty(newDocumentation);
        touch(now);
    }

    // ── Папки ───────────────────────────────────────────────────────

    /** Новая пользовательская папка в конце родителя; родитель должен быть в этой модели. */
    public ModelFolder createFolder(FolderId parentId, String folderName, FolderId newId, ArchiId newArchiId,
                                    Instant now) {
        XmlText.require(folderName, Message.of(ModelingMessages.FIELD_NAME));
        requireActive();
        requireFolder(parentId);
        requireFreeArchiId(newArchiId);
        ModelFolder folder = new ModelFolder(newId, parentId, newArchiId,
                Names.required(folderName, Names.FOLDER), null, nextOrderIn(parentId), null);
        folders.put(folder.id(), folder);
        touch(now);
        return folder;
    }

    /** Новое имя папки; пустое отклоняется. */
    public ModelFolder renameFolder(FolderId folderId, String newName, Instant now) {
        XmlText.require(newName, Message.of(ModelingMessages.FIELD_NAME));
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
            throw new ModelingDomainException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.ROOT_FOLDER_UNDELETABLE, folder.name()));
        }
        if (!childOrders(folderId).isEmpty()) {
            throw new ModelingDomainException(ModelingCodes.FOLDER_NOT_EMPTY, Failure.CONFLICT,
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
        FolderType targetRoot = Objects.requireNonNull(rootOf(requireFolder(targetId)).folderType());
        for (UUID itemId : itemIds) {
            requireMovable(itemId, targetId, targetRoot);
        }
        List<ViewRef> movedViews = new ArrayList<>();
        for (UUID itemId : itemIds) {
            if (folders.contains(FolderId.of(itemId))) {
                FolderId folderId = FolderId.of(itemId);
                folders.put(folderId, requireFolder(folderId).movedTo(targetId, nextOrderIn(targetId)));
            } else if (elements.contains(ElementId.of(itemId))) {
                Element element = elements.get(ElementId.of(itemId)).orElseThrow();
                elements.put(element.id(), element.movedTo(targetId, nextOrderIn(targetId)));
            } else if (relationships.contains(RelationshipId.of(itemId))) {
                Relationship relationship = relationships.get(RelationshipId.of(itemId)).orElseThrow();
                relationships.put(relationship.id(), relationship.movedTo(targetId, nextOrderIn(targetId)));
            } else {
                ViewRef view = views.get(ViewId.of(itemId));
                ViewRef moved = new ViewRef(view.id(), targetId, view.archiId(), view.archiType(), view.name(),
                        nextOrderIn(targetId));
                views.put(view.id(), moved);
                movedViews.add(moved);
            }
        }
        touch(now);
        return movedViews;
    }

    /**
     * Проверка одного объекта переноса — до любых изменений. У переноса одна целевая папка,
     * и ранние шаги кладут объекты только внутрь неё, не меняя её предков: проверка
     * по исходному дереву равносильна пошаговой.
     */
    private void requireMovable(UUID itemId, FolderId targetId, FolderType targetRoot) {
        if (folders.contains(FolderId.of(itemId))) {
            ModelFolder folder = requireFolder(FolderId.of(itemId));
            if (folder.root()) {
                throw new ModelingDomainException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                        Message.of(ModelingMessages.ROOT_FOLDER_UNMOVABLE, folder.name()));
            }
            if (isWithin(targetId, folder.id())) {
                throw new ModelingDomainException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                        Message.of(ModelingMessages.FOLDER_INTO_ITSELF, folder.name()));
            }
            requireSameRoot(folder.id(), targetRoot, Message.of(ModelingMessages.FOLDER, folder.name()));
        } else if (elements.contains(ElementId.of(itemId))) {
            Element element = elements.get(ElementId.of(itemId)).orElseThrow();
            requireSameRoot(element.folderId(), targetRoot, Message.of(ModelingMessages.ELEMENT, element.name()));
        } else if (relationships.contains(RelationshipId.of(itemId))) {
            Relationship relationship = relationships.get(RelationshipId.of(itemId)).orElseThrow();
            requireSameRoot(relationship.folderId(), targetRoot,
                    Message.of(ModelingMessages.RELATIONSHIP, relationship.archiId()));
        } else if (views.containsKey(ViewId.of(itemId))) {
            ViewRef view = views.get(ViewId.of(itemId));
            requireSameRoot(view.folderId(), targetRoot, Message.of(ModelingMessages.VIEW, view.name()));
        } else {
            throw ModelingDomainException.notFound(Message.of(ModelingMessages.OBJECT_IN_MODEL, itemId));
        }
    }

    private void requireSameRoot(FolderId current, FolderType targetRoot, Message what) {
        FolderType currentRoot = Objects.requireNonNull(rootOf(requireFolder(current)).folderType());
        if (currentRoot != targetRoot) {
            throw new ModelingDomainException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.WRONG_ROOT, what, currentRoot.fileValue(), targetRoot.fileValue()));
        }
    }

    // ── Элементы ────────────────────────────────────────────────────

    /**
     * Новый элемент в папке своего слоя. Тип — поддержанный текущей фазой: тип фазы 3
     * и тип вне фаз создать нельзя, импортированный остаётся opaque.
     */
    public Element addElement(ArchiType archiType, String elementName, FolderId folderId, ElementId newId,
                              ArchiId newArchiId, Instant now) {
        XmlText.require(elementName, Message.of(ModelingMessages.FIELD_NAME));
        requireActive();
        ConceptDefinition concept = REGISTRY.find(archiType)
                .filter(c -> c.kind() == ConceptKind.ELEMENT || c.kind() == ConceptKind.JUNCTION)
                .orElseThrow(() -> ModelingDomainException.invalid(
                        Message.of(ModelingMessages.NOT_ELEMENT_TYPE, archiType)));
        if (!concept.supported()) {
            throw new ModelingDomainException(ModelingCodes.TYPE_NOT_EDITABLE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.TYPE_NOT_EDITABLE, archiType));
        }
        requireSameRoot(folderId, FolderType.forLayer(concept.layer()),
                Message.of(ModelingMessages.ELEMENT_OF_TYPE, archiType.simpleName()));
        requireFreeArchiId(newArchiId);
        Element element = new Element(newId, folderId, newArchiId, archiType, Names.limited(elementName, Names.ELEMENT),
                null, List.of(), nextOrderIn(folderId), true, null);
        elements.put(element.id(), element);
        touch(now);
        return element;
    }

    /**
     * Правка имени, документации и свойств элемента; нередактируемый тип отклоняется.
     * {@code null} и пустая строка в документации — документации нет.
     */
    public Element updateElement(ElementId elementId, String newName, String newDocumentation,
                                 List<PropertyEntry> newProperties, Instant now) {
        XmlText.require(newName, Message.of(ModelingMessages.FIELD_NAME));
        XmlText.require(newDocumentation, Message.of(ModelingMessages.FIELD_DOCUMENTATION));
        XmlText.require(newProperties);
        requireActive();
        Element element = requireElement(elementId);
        requireEditable(element.supported(), element.archiType());
        Element edited = element.edited(Names.limited(newName, Names.ELEMENT),
                nonEmpty(newDocumentation), newProperties);
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
     * существующая связь. {@code null} и пустая строка в имени — имени нет.
     */
    public Created<Relationship> addRelationship(ArchiType archiType, ConceptRef source, ConceptRef target,
                                                 String relationshipName, RelationshipId newId,
                                                 ArchiId newArchiId, Instant now) {
        XmlText.require(relationshipName, Message.of(ModelingMessages.FIELD_NAME));
        return addRelationship(archiType, source, target, relationshipName, null, null,
                null, newId, newArchiId, now);
    }

    /**
     * То же с местом в дереве и атрибутами типа. Папка — в поддереве {@code Relations},
     * без неё связь ложится в корень; вид доступа бывает только у Access, направленность —
     * только у Association. {@code null} в любом из необязательных аргументов — не задан.
     */
    public Created<Relationship> addRelationship(ArchiType archiType, ConceptRef source, ConceptRef target,
                                                 String relationshipName, FolderId folderId,
                                                 AccessType accessType, Boolean directed,
                                                 RelationshipId newId, ArchiId newArchiId, Instant now) {
        XmlText.require(relationshipName, Message.of(ModelingMessages.FIELD_NAME));
        requireActive();
        RelationshipType type = RelationshipType.fromArchiType(archiType)
                .orElseThrow(() -> ModelingDomainException.invalid(
                        Message.of(ModelingMessages.NOT_RELATIONSHIP_TYPE, archiType)));
        if (accessType != null && type != RelationshipType.ACCESS) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.ACCESS_TYPE_MISPLACED, archiType));
        }
        if (directed != null && type != RelationshipType.ASSOCIATION) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.DIRECTED_MISPLACED, archiType));
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
        FolderId folder = folderId != null ? folderId : roots().get(FolderType.RELATIONS).id();
        requireSameRoot(folder, FolderType.RELATIONS,
                Message.of(ModelingMessages.RELATIONSHIP_OF_TYPE, archiType.simpleName()));
        requireFreeArchiId(newArchiId);
        Relationship relationship = new Relationship(newId, folder, newArchiId, archiType, source, target,
                nonEmpty(relationshipName), null, accessType, directed,
                List.of(), nextOrderIn(folder), true, null);
        relationships.put(relationship.id(), relationship);
        touch(now);
        return new Created<>(relationship, true);
    }

    /** Правка имени, документации и свойств связи; {@code null} и пустая строка — значения нет. */
    public Relationship updateRelationship(RelationshipId relationshipId, String newName,
                                           String newDocumentation, List<PropertyEntry> newProperties,
                                           Instant now) {
        XmlText.require(newName, Message.of(ModelingMessages.FIELD_NAME));
        XmlText.require(newDocumentation, Message.of(ModelingMessages.FIELD_DOCUMENTATION));
        XmlText.require(newProperties);
        requireActive();
        Relationship relationship = requireRelationship(relationshipId);
        requireEditable(relationship.supported(), relationship.archiType());
        Relationship edited = relationship.edited(
                newName == null || newName.isEmpty() ? null : Names.limited(newName, Names.RELATIONSHIP),
                nonEmpty(newDocumentation), newProperties);
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
            throw new ModelingDomainException(ModelingCodes.RELATIONSHIP_ENDS, Failure.CONFLICT,
                    Message.of(ModelingMessages.HAS_RELATIONSHIPS, what, touching.size()),
                    Map.of("relationships", touching.stream().map(r -> r.id().toString()).toList()));
        }
    }

    // ── Представления в дереве ──────────────────────────────────────

    /** Место для нового представления: папка в поддереве {@code diagrams}. */
    public ViewRef placeNewView(ViewId viewId, DiagramType archiType, String viewName, FolderId folderId,
                                ArchiId newArchiId, Instant now) {
        XmlText.require(viewName, Message.of(ModelingMessages.FIELD_NAME));
        requireActive();
        requireSameRoot(folderId, FolderType.DIAGRAMS, Message.of(ModelingMessages.VIEW, viewName));
        requireFreeArchiId(newArchiId);
        ViewRef ref = new ViewRef(viewId, folderId, newArchiId, archiType, Names.limited(viewName, Names.VIEW),
                nextOrderIn(folderId));
        views.put(viewId, ref);
        touch(now);
        return ref;
    }

    /** Модель забывает представление: его место в дереве освобождается. */
    public void forgetView(ViewId viewId, Instant now) {
        requireActive();
        views.remove(viewId);
        touch(now);
    }

    /**
     * Новое имя в месте представления — у активной модели и в пределах длины имени;
     * вызывается вместе с переименованием самого представления.
     */
    public void renameView(ViewId viewId, String newName, Instant now) {
        XmlText.require(newName, Message.of(ModelingMessages.FIELD_NAME));
        requireActive();
        ViewRef view = view(viewId)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW, viewId)));
        views.put(viewId, new ViewRef(view.id(), view.folderId(), view.archiId(), view.archiType(),
                Names.limited(newName, Names.VIEW), view.sortOrder()));
        touch(now);
    }

    // ── Проверки и выборки ──────────────────────────────────────────

    /** Папка этой модели; чужая или несуществующая — отказ. */
    public ModelFolder requireFolder(FolderId folderId) {
        return folders.get(folderId)
                .orElseThrow(() -> new ModelingDomainException(ModelingCodes.NOT_FOUND, Failure.UNPROCESSABLE,
                        Message.of(ModelingMessages.FOLDER_OF_OTHER_MODEL, folderId, id())));
    }

    /** Элемент этой модели; нет такого — «не найдено». */
    public Element requireElement(ElementId elementId) {
        return elements.get(elementId)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.ELEMENT, elementId)));
    }

    /** Связь этой модели; нет такой — «не найдено». */
    public Relationship requireRelationship(RelationshipId relationshipId) {
        return relationships.get(relationshipId)
                .orElseThrow(() -> ModelingDomainException.notFound(
                        Message.of(ModelingMessages.RELATIONSHIP, relationshipId)));
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
            throw new ModelingDomainException(ModelingCodes.RELATIONSHIP_ENDS, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.END_OF_OTHER_MODEL, concept.value(), id()));
        }
    }

    private static void requireEditable(boolean supported, ArchiType archiType) {
        if (!supported) {
            throw new ModelingDomainException(ModelingCodes.TYPE_NOT_EDITABLE, Failure.UNPROCESSABLE,
                    Message.of(ModelingMessages.OPAQUE_NOT_EDITABLE, archiType));
        }
    }

    /** {@code archiId} не повторяется в модели. */
    public void requireFreeArchiId(ArchiId candidate) {
        if (archiIdTaken(candidate)) {
            throw new ModelingDomainException(ModelingCodes.ARCHI_ID_UNIQUE, Failure.CONFLICT,
                    Message.of(ModelingMessages.ARCHI_ID_TAKEN, candidate, id()));
        }
    }

    /** Занят ли {@code archiId} самой моделью, её папкой, элементом, связью или представлением. */
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
        folders.values().stream().filter(ModelFolder::root).forEach(f -> roots.put(f.folderType(), f));
        return roots;
    }

    /** Корень поддерева, в котором лежит папка; цикл в дереве — нарушение структуры папок. */
    public ModelFolder rootOf(FolderId folderId) {
        return rootOf(requireFolder(folderId));
    }

    private ModelFolder rootOf(ModelFolder folder) {
        ModelFolder current = folder;
        Set<FolderId> visited = new HashSet<>();
        while (current.parentId() != null) {
            if (!visited.add(current.id())) {
                throw new ModelingDomainException(ModelingCodes.FOLDER_TREE, Failure.UNPROCESSABLE,
                        Message.of(ModelingMessages.FOLDER_CYCLE, current.archiId()));
            }
            current = requireFolder(current.parentId());
        }
        return current;
    }

    /** Лежит ли {@code candidate} в поддереве {@code ancestor}, включая его самого. */
    private boolean isWithin(FolderId candidate, FolderId ancestor) {
        FolderId current = candidate;
        while (current != null) {
            if (current.equals(ancestor)) {
                return true;
            }
            current = requireFolder(current).parentId();
        }
        return false;
    }

    /** Позиции всех, кто лежит в папке: подпапки, элементы, связи, представления. */
    private List<SortOrder> childOrders(FolderId folderId) {
        List<SortOrder> orders = new ArrayList<>();
        folders.values().stream().filter(f -> folderId.equals(f.parentId()))
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

    /** Пустая строка — значения нет. */
    private static String nonEmpty(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    // ── Состояние для хранилища и чтения ────────────────────────────

    /** Собственные поля модели для записи в хранилище. */
    public ModelHeader header() {
        return new ModelHeader(id(), workspaceId, archiId, name, documentation, archiVersion, status, properties,
                rawXml, createdBy, createdAt, updatedAt, version);
    }


    /** Пространство, которому принадлежит модель. */
    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    /** Идентификатор модели в файле Archi. */
    public ArchiId archiId() {
        return archiId;
    }

    /** Текущее имя модели. */
    public String name() {
        return name;
    }

    /** Состояние жизненного цикла. */
    public ModelStatus status() {
        return status;
    }

    /** Subject автора модели. */
    public String createdBy() {
        return createdBy;
    }

    /** Версия строки модели для оптимистичной блокировки. */
    public long version() {
        return version;
    }

    /** Ещё не записана в хранилище: создана или импортирована в этой транзакции. */
    public boolean fresh() {
        return fresh;
    }

    /** Нужно ли записать заголовок модели: модель новая или её поля менялись. */
    public boolean headerChanged() {
        return fresh || headerChanged;
    }

    /** Папки с учётом изменений. */
    public TrackedEntities<FolderId, ModelFolder> folders() {
        return folders;
    }

    /** Элементы с учётом изменений. */
    public TrackedEntities<ElementId, Element> elements() {
        return elements;
    }

    /** Связи с учётом изменений. */
    public TrackedEntities<RelationshipId, Relationship> relationships() {
        return relationships;
    }

    /** Хранилище записало папки, элементы и связи: всё текущее теперь сохранено. */
    public void markPersisted() {
        folders.markPersisted();
        elements.markPersisted();
        relationships.markPersisted();
    }

    /** Места представлений в дереве модели, копией. */
    public Collection<ViewRef> views() {
        return List.copyOf(views.values());
    }

    /** Место представления в дереве; пусто — в этой модели его нет. */
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
