package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
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
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
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
     * @param folderId   папка в поддереве {@code Relations}; пусто — его корень
     * @param accessType только у Access
     * @param directed   только у Association
     * @param ids        идентификаторы связи, выбранные клиентом
     */
    public record Details(Optional<String> name, Optional<FolderId> folderId, Optional<AccessType> accessType,
                          Optional<Boolean> directed, RequestedIds ids) {

        public static Details named(Optional<String> name) {
            return new Details(name, Optional.empty(), Optional.empty(), Optional.empty(), RequestedIds.NONE);
        }
    }

    /** Итог: связь и, если просили, её ребро; {@code created} ложно у возвращённого дубля. */
    public record Result(Relationship relationship, boolean created, Optional<ViewEdgeId> edge) {
    }

    public Result create(EditorIdentity actor, ModelId modelId, ArchiType archiType, UUID sourceId, UUID targetId,
                         Optional<String> name, Optional<EdgePlacement> placement, Optional<String> idempotencyKey) {
        return create(actor, modelId, archiType, sourceId, targetId, Details.named(name), placement, idempotencyKey);
    }

    /**
     * Связь-дубль того же типа между той же парой возвращается как есть: идентификаторы
     * и атрибуты из запроса к ней не применяются.
     */
    public Result create(EditorIdentity actor, ModelId modelId, ArchiType archiType, UUID sourceId, UUID targetId,
                         Details details, Optional<EdgePlacement> placement, Optional<String> idempotencyKey) {
        return kernel.run(Operation.CREATE_RELATIONSHIP, actor, () -> kernel.unitOfWork.write(() -> {
            boolean[] created = {false};
            Optional<ViewEdgeId>[] edge = new Optional[] {Optional.empty()};
            Relationship relationship = kernel.idempotent("CreateRelationship", actor, idempotencyKey,
                    IdempotentCommand.fingerprint(modelId, archiType, sourceId, targetId, details, placement),
                    () -> {
                        ArchitectureModel model = kernel.writableModel(modelId, actor);
                        ConceptRef source = concept(model, sourceId);
                        ConceptRef target = concept(model, targetId);
                        Optional<Relationship> duplicate = model.relationships().values().stream()
                                .filter(r -> r.archiType().equals(archiType) && r.source().equals(source)
                                        && r.target().equals(target))
                                .findFirst();
                        var result = duplicate.isPresent()
                                ? new ArchitectureModel.Created<>(duplicate.get(), false)
                                : model.addRelationship(archiType, source, target, details.name(), details.folderId(),
                                        details.accessType(), details.directed(),
                                        RelationshipId.of(kernel.newId(details.ids())),
                                        kernel.newArchiId(model, details.ids()), kernel.now());
                        created[0] = result.created();
                        kernel.save(model);
                        placement.ifPresent(p -> edge[0] = Optional.of(drawEdge(model, result.value(), p)));
                        return result.value().id().toString();
                    },
                    ref -> kernel.models.load(modelId).orElseThrow().requireRelationship(RelationshipId.of(ref)));
            return new Result(relationship, created[0], edge[0]);
        }));
    }

    private ViewEdgeId drawEdge(ArchitectureModel model, Relationship relationship, EdgePlacement placement) {
        View view = kernel.views.load(placement.viewId())
                .filter(v -> v.modelId().equals(model.id()))
                .orElseThrow(() -> ModelingException.notFound("представление " + placement.viewId()));
        ViewEndpoint source = ViewService.endpoint(view, placement.sourceEndpoint());
        ViewEndpoint target = ViewService.endpoint(view, placement.targetEndpoint());
        Optional<ViewEdge> drawn = view.edges().values().stream()
                .filter(e -> e.relationshipId().equals(Optional.of(relationship.id()))
                        && e.source().equals(source) && e.target().equals(target))
                .findFirst();
        if (drawn.isPresent()) {
            return drawn.get().id();
        }
        var edge = view.connect(model, relationship.id(), source, target,
                ViewEdgeId.of(kernel.newId(placement.edgeIds())),
                kernel.newArchiId(model, view, placement.edgeIds()));
        kernel.views.save(view);
        return edge.value().id();
    }

    public Relationship update(EditorIdentity actor, RelationshipId relationshipId, Optional<String> name,
                               Optional<String> documentation, Optional<List<ElementService.PropertyValue>> properties) {
        return kernel.run(Operation.UPDATE_RELATIONSHIP, actor, () -> kernel.unitOfWork.write(() -> {
            ArchitectureModel model = kernel.writableModel(elements.ownerOf(relationshipId.value()), actor);
            Relationship current = model.requireRelationship(relationshipId);
            Relationship updated = model.updateRelationship(relationshipId, name.isPresent() ? name : current.name(),
                    documentation.isPresent() ? documentation : current.documentation(),
                    properties.map(ElementService::numbered).orElse(current.properties()), kernel.now());
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

    /** Допустимые типы для упорядоченной пары; первый — выбор по умолчанию. */
    public static List<RelationshipType> suggest(ArchiType sourceType, ArchiType targetType) {
        return RelationMatrix.archimate32().permitted(sourceType, targetType).stream()
                .sorted(Comparator.comparingInt(PREFERENCE::indexOf)).toList();
    }

    private static ConceptRef concept(ArchitectureModel model, UUID id) {
        return model.elements().contains(ElementId.of(id)) ? ElementId.of(id) : RelationshipId.of(id);
    }
}
