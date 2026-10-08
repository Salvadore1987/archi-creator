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
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ConceptRef;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** UC-MDL-003: создать связь по матрице, предложить допустимые типы, изменить и удалить (FR-09, FR-10). */
public final class RelationshipService {

    /**
     * Порядок, в котором типы предлагаются: первый допустимый — выбор по умолчанию
     * (UC-MDL-003, п. 2). Наиболее частые в ландшафте — впереди.
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

    /** Ребро для новой связи на представлении: концы — узлы или рёбра этого представления. */
    public record EdgePlacement(ViewId viewId, UUID sourceEndpoint, UUID targetEndpoint) {
    }

    /** Итог: связь и, если просили, её ребро; {@code created} ложно у возвращённого дубля. */
    public record Result(Relationship relationship, boolean created, Optional<ViewEdgeId> edge) {
    }

    public Result create(EditorIdentity actor, ModelId modelId, ArchiType archiType, UUID sourceId, UUID targetId,
                         Optional<String> name, Optional<EdgePlacement> placement, Optional<String> idempotencyKey) {
        return kernel.run(Operation.CREATE_RELATIONSHIP, actor, () -> kernel.unitOfWork.write(() -> {
            boolean[] created = {false};
            Optional<ViewEdgeId>[] edge = new Optional[] {Optional.empty()};
            Relationship relationship = kernel.idempotent("CreateRelationship", actor, idempotencyKey,
                    IdempotentCommand.fingerprint(modelId, archiType, sourceId, targetId, name, placement),
                    () -> {
                        ArchitectureModel model = kernel.writableModel(modelId, actor);
                        var result = model.addRelationship(archiType, concept(model, sourceId),
                                concept(model, targetId), name, RelationshipId.next(kernel.uuids),
                                kernel.newArchiId(model), kernel.now());
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
        var edge = view.connect(model, relationship.id(), endpoint(view, placement.sourceEndpoint()),
                endpoint(view, placement.targetEndpoint()), ViewEdgeId.next(kernel.uuids),
                kernel.archiIds.next());
        kernel.views.save(view);
        return edge.value().id();
    }

    private static ViewEndpoint endpoint(View view, UUID id) {
        return view.nodes().contains(ViewNodeId.of(id)) ? ViewNodeId.of(id) : ViewEdgeId.of(id);
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

    /** Удаление связи снимает её рёбра на представлениях; примыкающая связь — {@code 409} (INV-MDL-004). */
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

    /** Допустимые типы для упорядоченной пары; первый — выбор по умолчанию (UC-MDL-003, п. 1–2). */
    public static List<RelationshipType> suggest(ArchiType sourceType, ArchiType targetType) {
        return RelationMatrix.archimate32().permitted(sourceType, targetType).stream()
                .sorted(Comparator.comparingInt(PREFERENCE::indexOf)).toList();
    }

    private static ConceptRef concept(ArchitectureModel model, UUID id) {
        return model.elements().contains(ElementId.of(id)) ? ElementId.of(id) : RelationshipId.of(id);
    }
}
