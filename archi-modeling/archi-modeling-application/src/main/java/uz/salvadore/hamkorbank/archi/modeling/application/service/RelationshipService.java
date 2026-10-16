package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.NestingRules;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationMatrix;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ConceptRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdge;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/** Создать связь по матрице, предложить допустимые типы, изменить и удалить. */
public final class RelationshipService {

    /**
     * Порядок, в котором типы предлагаются: первый допустимый — выбор по умолчанию.
     * Наиболее частые в ландшафте — впереди.
     */
    private static final List<RelationshipType> PREFERENCE = List.of(
            RelationshipType.SERVING, RelationshipType.REALIZATION, RelationshipType.ASSIGNMENT,
            RelationshipType.COMPOSITION, RelationshipType.AGGREGATION, RelationshipType.FLOW,
            RelationshipType.TRIGGERING, RelationshipType.ACCESS, RelationshipType.INFLUENCE,
            RelationshipType.SPECIALIZATION, RelationshipType.ASSOCIATION);

    private final ModelingKernel kernel;
    private final ElementService elements;

    public RelationshipService(ModelingKernel kernel, ElementService elements) {
        this.kernel = kernel;
        this.elements = elements;
    }

    /**
     * Ребро для новой связи на представлении: концы — узлы или рёбра этого представления.
     *
     * @param edgeIds идентификаторы ребра, выбранные клиентом
     */
    public record EdgePlacement(ViewId viewId, UUID sourceEndpoint, UUID targetEndpoint, RequestedIds edgeIds) {

        public EdgePlacement(ViewId viewId, UUID sourceEndpoint, UUID targetEndpoint) {
            this(viewId, sourceEndpoint, targetEndpoint, RequestedIds.NONE);
        }
    }

    /**
     * Что создать, кроме концов и типа.
     *
     * @param name       имя; {@code null} — без имени
     * @param folderId   папка в поддереве {@code Relations}; {@code null} — его корень
     * @param accessType только у Access; {@code null} — не задан
     * @param directed   только у Association; {@code null} — не задано
     * @param ids        идентификаторы связи, выбранные клиентом
     */
    public record Details(String name, FolderId folderId, AccessType accessType,
                          Boolean directed, RequestedIds ids) {

        /** Детали, в которых задано только имя ({@code null} — без имени). */
        public static Details named(String name) {
            return new Details(name, null, null, null, RequestedIds.NONE);
        }
    }

    /**
     * Итог: связь и, если просили, её ребро; {@code created} ложно у возвращённого дубля.
     *
     * @param edge ребро на представлении; {@code null} — ребро не просили
     */
    public record Result(Relationship relationship, boolean created, ViewEdgeId edge) {
    }

    /**
     * Создание связи только с именем и идентификаторами от сервера; дубль возвращается как есть.
     * {@code null} в имени, размещении и ключе — не заданы.
     */
    public Result create(EditorIdentity actor, ModelId modelId, ArchiType archiType, UUID sourceId, UUID targetId,
                         String name, EdgePlacement placement, String idempotencyKey) {
        return create(actor, modelId, archiType, sourceId, targetId, Details.named(name), placement, idempotencyKey);
    }

    /**
     * Связь-дубль того же типа между той же парой возвращается как есть: идентификаторы
     * и атрибуты из запроса к ней не применяются. {@code null} в размещении — ребро не рисуется,
     * в ключе — без идемпотентности.
     */
    public Result create(EditorIdentity actor, ModelId modelId, ArchiType archiType, UUID sourceId, UUID targetId,
                         Details details, EdgePlacement placement, String idempotencyKey) {
        return kernel.run(Operation.CREATE_RELATIONSHIP, actor, () -> kernel.unitOfWork.write(() -> {
            boolean[] created = {false};
            ViewEdgeId[] edge = {null};
            Relationship relationship = kernel.idempotent("CreateRelationship", actor, idempotencyKey,
                    fingerprint(modelId, archiType, sourceId, targetId, details, placement),
                    () -> {
                        ArchitectureModel model = kernel.writableModel(modelId, actor);
                        ConceptRef source = concept(model, sourceId);
                        ConceptRef target = concept(model, targetId);
                        ArchitectureModel.Created<Relationship> result = model.addRelationship(archiType, source,
                                target, details.name(), details.folderId(), details.accessType(), details.directed(),
                                () -> RelationshipId.of(kernel.newId(details.ids())),
                                () -> kernel.newArchiId(model, details.ids()), kernel.now());
                        created[0] = result.created();
                        kernel.save(model);
                        if (placement != null) {
                            edge[0] = drawEdge(model, result.value(), placement);
                        }
                        return result.value().id().toString();
                    },
                    ref -> kernel.models.load(modelId).orElseThrow().requireRelationship(RelationshipId.of(ref)));
            return new Result(relationship, created[0], edge[0]);
        }));
    }

    /** Отпечаток команды по полям, без {@code toString} её частей: новое поле не сдвинет старые ключи. */
    private static String fingerprint(ModelId modelId, ArchiType archiType, UUID sourceId, UUID targetId,
                                      Details details, EdgePlacement placement) {
        return IdempotentCommand.fingerprint(modelId, archiType.value(), sourceId, targetId, details.name(),
                details.folderId(), details.accessType(), details.directed(), details.ids().id(),
                details.ids().archiId(), placement == null ? null : placement.viewId(),
                placement == null ? null : placement.sourceEndpoint(),
                placement == null ? null : placement.targetEndpoint(),
                placement == null ? null : placement.edgeIds().id(),
                placement == null ? null : placement.edgeIds().archiId());
    }

    private ViewEdgeId drawEdge(ArchitectureModel model, Relationship relationship, EdgePlacement placement) {
        View view = kernel.views.load(placement.viewId())
                .filter(v -> v.modelId().equals(model.id()))
                .orElseThrow(() -> ModelingDomainException.notFound(
                        Message.of(ModelingMessages.VIEW, placement.viewId())));
        ArchitectureModel.Created<ViewEdge> edge = view.connect(model, relationship.id(),
                ViewService.endpoint(view, placement.sourceEndpoint()),
                ViewService.endpoint(view, placement.targetEndpoint()), List.of(),
                () -> ViewEdgeId.of(kernel.newId(placement.edgeIds())),
                () -> kernel.newArchiId(model, view, placement.edgeIds()));
        if (edge.created()) {
            kernel.views.save(view);
        }
        return edge.value().id();
    }

    /**
     * Правка имени, описания и свойств связи; незаданное ({@code null}) поле остаётся прежним.
     * Нужна блокировка модели.
     */
    public Relationship update(EditorIdentity actor, RelationshipId relationshipId, String name,
                               String documentation,
                               List<ElementService.PropertyValue> properties) {
        return kernel.run(Operation.UPDATE_RELATIONSHIP, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(elements.ownerOf(relationshipId.value()), actor);
            Relationship current = model.requireRelationship(relationshipId);
            Relationship updated = model.updateRelationship(relationshipId, name != null ? name : current.name(),
                    documentation != null ? documentation : current.documentation(),
                    properties != null ? ElementService.numbered(properties) : current.properties(), kernel.now());
            kernel.save(model);
            return updated;
        }));
    }

    /** Удаление связи снимает её рёбра на представлениях; примыкающая связь — {@code 409}. */
    public void delete(EditorIdentity actor, RelationshipId relationshipId) {
        kernel.run(Operation.DELETE_RELATIONSHIP, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(elements.ownerOf(relationshipId.value()), actor);
            deleteIn(model, relationshipId);
            kernel.save(model);
            return null;
        }));
    }

    void deleteIn(ArchitectureModel model, RelationshipId relationshipId) {
        model.removeRelationship(relationshipId, kernel.now());
        for (ViewId viewId : kernel.models.viewsReferencing(relationshipId)) {
            View view = kernel.views.load(viewId).orElseThrow();
            if (view.removeRelationshipReferences(relationshipId)) {
                kernel.views.save(view);
            }
        }
    }

    /**
     * Допустимый тип связи для пары в том порядке, в котором его предлагать.
     *
     * @param byDefault выбран по умолчанию — первый в списке
     * @param nesting   этот тип подразумевает вложение цели в источник
     */
    public record Suggestion(RelationshipType type, boolean byDefault, boolean nesting) {
    }

    /** Что предложить при рисовании связи от {@code sourceType} к {@code targetType}. */
    public static List<Suggestion> suggestions(ArchiType sourceType, ArchiType targetType) {
        List<RelationshipType> permitted = suggest(sourceType, targetType);
        Optional<RelationshipType> nesting = NestingRules.archimate32().impliedRelationship(sourceType, targetType);
        return java.util.stream.IntStream.range(0, permitted.size())
                .mapToObj(i -> new Suggestion(permitted.get(i), i == 0,
                        nesting.equals(Optional.of(permitted.get(i)))))
                .toList();
    }

    /** Допустимые типы для упорядоченной пары; первый — выбор по умолчанию. */
    public static List<RelationshipType> suggest(ArchiType sourceType, ArchiType targetType) {
        return RelationMatrix.archimate32().permitted(sourceType, targetType).stream()
                .sorted(Comparator.comparingInt(PREFERENCE::indexOf)).toList();
    }

    private static ConceptRef concept(ArchitectureModel model, UUID id) {
        return model.elements().contains(ElementId.of(id)) ? ElementId.of(id) : RelationshipId.of(id);
    }
}
