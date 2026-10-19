package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
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

/**
 * Связь по матрице допустимых и, если просят, её ребро на представлении. Связь-дубль того же
 * типа между той же парой возвращается как есть: идентификаторы и атрибуты из команды к ней
 * не применяются.
 */
public final class RelationshipCreateCommandHandler {

    private final ModelingKernel kernel;

    public RelationshipCreateCommandHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Новая связь или дубль; повтор ключа — тот же результат. */
    public CreateRelationshipResponse handle(EditorIdentity actor, CreateRelationshipCommand command) {
        return kernel.run(Operation.CREATE_RELATIONSHIP, actor, () -> {
            ArchiType archiType = RequestValues.archiType(command.archiType());
            AccessType accessType = RequestValues.accessType(command.accessType());
            ModelId modelId = ModelId.of(command.modelId());
            FolderId folderId = command.folderId() == null ? null : FolderId.of(command.folderId());
            RequestedIds ids = RequestedIds.of(command.id(), command.archiId());
            return kernel.unitOfWork().write(() -> {
                boolean[] created = {false};
                ViewEdgeId[] edge = {null};
                Relationship relationship = kernel.idempotent("CreateRelationship", actor, command.idempotencyKey(),
                        fingerprint(command, modelId, archiType, folderId, accessType),
                        () -> {
                            ArchitectureModel model = kernel.writableModel(modelId, actor);
                            ArchitectureModel.Created<Relationship> result = model.addRelationship(archiType,
                                    concept(model, command.sourceId()), concept(model, command.targetId()),
                                    command.name(), folderId, accessType, command.directed(),
                                    () -> RelationshipId.of(kernel.newId(ids)),
                                    () -> kernel.newArchiId(model, ids), kernel.now());
                            created[0] = result.created();
                            kernel.save(model);
                            if (command.edge() != null) {
                                edge[0] = drawEdge(model, result.value(), command.edge());
                            }
                            return result.value().id().toString();
                        },
                        ref -> kernel.models().load(modelId).orElseThrow().requireRelationship(RelationshipId.of(ref)));
                return new CreateRelationshipResponse(ModelDataMapper.relationship(relationship), created[0],
                        edge[0] == null ? null : edge[0].value());
            });
        });
    }

    /** Отпечаток команды по полям, без {@code toString} её частей: новое поле не сдвинет старые ключи. */
    private static String fingerprint(CreateRelationshipCommand command, ModelId modelId, ArchiType archiType,
                                      FolderId folderId, AccessType accessType) {
        CreateRelationshipCommand.Edge edge = command.edge();
        return IdempotentCommand.fingerprint(modelId, archiType.value(), command.sourceId(), command.targetId(),
                command.name(), folderId, accessType, command.directed(), command.id(), command.archiId(),
                edge == null ? null : ViewId.of(edge.viewId()),
                edge == null ? null : edge.sourceEndpoint(),
                edge == null ? null : edge.targetEndpoint(),
                edge == null ? null : edge.edgeId(),
                edge == null ? null : edge.edgeArchiId());
    }

    private ViewEdgeId drawEdge(ArchitectureModel model, Relationship relationship,
                                CreateRelationshipCommand.Edge placement) {
        ViewId viewId = ViewId.of(placement.viewId());
        RequestedIds edgeIds = RequestedIds.of(placement.edgeId(), placement.edgeArchiId());
        View view = kernel.views().load(viewId)
                .filter(v -> v.modelId().equals(model.id()))
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW, viewId)));
        ArchitectureModel.Created<ViewEdge> edge = view.connect(model, relationship.id(),
                ViewEndpoints.of(view, placement.sourceEndpoint()),
                ViewEndpoints.of(view, placement.targetEndpoint()), List.of(),
                () -> ViewEdgeId.of(kernel.newId(edgeIds)),
                () -> kernel.newArchiId(model, view, edgeIds));
        if (edge.created()) {
            kernel.views().save(view);
        }
        return edge.value().id();
    }

    private static ConceptRef concept(ArchitectureModel model, UUID id) {
        return model.elements().contains(ElementId.of(id)) ? ElementId.of(id) : RelationshipId.of(id);
    }
}
