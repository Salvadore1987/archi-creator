package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import uz.salvadore.hamkorbank.archi.advisor.application.port.ModelContextReader;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextElement;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextLayer;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextRelation;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.GraphView;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ModelGraph;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlacementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.RelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelStatus;

/**
 * Мост помощника к modeling — только чтение, через сценарий {@link ModelQueryApplicationService}:
 * роль, список доступа и транзакция чтения — его. Модель переводится в граф контекста
 * (анти-коррупционный слой): типы, имена, документация, связи и что где размещено —
 * без папок, свойств, стиля и геометрии.
 *
 * <p>Это единственное место, где встречаются оба контекста. Сервисов записи modeling
 * здесь нет и быть не должно — это держит архитектурный тест.
 */
public final class ModelingContextReader implements ModelContextReader {

    private final ModelQueryApplicationService models;

    public ModelingContextReader(ModelQueryApplicationService models) {
        this.models = models;
    }

    @Override
    public ReadModel read(Requester requester, ModelId modelId) {
        ModelTreeResponse tree;
        try {
            tree = models.getModel(editor(requester), new GetModelQuery(modelId.value()));
        } catch (ModelingDomainException hidden) {
            // Нет модели или она скрыта списком доступа — для помощника одно и то же.
            throw AdvisorDomainException.notFound(Message.of(AdvisorMessages.MODEL, modelId));
        }
        Map<UUID, String> archiIds = new HashMap<>();
        tree.elements().forEach(e -> archiIds.put(e.id(), e.archiId()));
        tree.relationships().forEach(r -> archiIds.put(r.id(), r.archiId()));

        List<ContextElement> elements = tree.elements().stream()
                .sorted(Comparator.comparingLong(ElementResponse::sortOrder).thenComparing(ElementResponse::archiId))
                .map(e -> new ContextElement(e.archiId(), e.archiType(), e.name(), e.documentation(),
                        ContextLayer.valueOf(e.layer())))
                .toList();
        List<ContextRelation> relations = tree.relationships().stream()
                .sorted(Comparator.comparingLong(RelationshipResponse::sortOrder)
                        .thenComparing(RelationshipResponse::archiId))
                .map(r -> new ContextRelation(r.archiId(), r.archiType(), archiIds.get(r.sourceId()),
                        archiIds.get(r.targetId())))
                .toList();
        Map<UUID, List<UUID>> placed = tree.placements().stream()
                .collect(Collectors.toMap(PlacementResponse::viewId, PlacementResponse::elementIds));
        List<GraphView> views = tree.views().stream()
                .map(v -> new GraphView(ViewId.of(v.id()), v.archiId(), v.name(),
                        placed.getOrDefault(v.id(), List.of()).stream().map(archiIds::get).toList()))
                .toList();
        return new ReadModel(WorkspaceId.of(tree.model().workspaceId()),
                !ModelStatus.ACTIVE.name().equals(tree.model().status()), new ModelGraph(elements, relations, views));
    }

    static EditorIdentity editor(Requester requester) {
        Set<Role> roles = requester.roles().stream().map(r -> Role.valueOf(r.name())).collect(Collectors.toSet());
        return new EditorIdentity(requester.subject(), roles, requester.groups());
    }
}
