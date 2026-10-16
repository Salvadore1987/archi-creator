package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelQueryService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ViewRef;

/**
 * Мост помощника к modeling — только чтение, через сценарий {@link ModelQueryService}:
 * роль, список доступа и транзакция чтения — его. Модель переводится в граф контекста
 * (анти-коррупционный слой): типы, имена, документация, связи и что где размещено —
 * без папок, свойств, стиля и геометрии.
 *
 * <p>Это единственное место, где встречаются оба контекста. Сервисов записи modeling
 * здесь нет и быть не должно — это держит архитектурный тест.
 */
public final class ModelingContextReader implements ModelContextReader {

    private final ModelQueryService models;

    public ModelingContextReader(ModelQueryService models) {
        this.models = models;
    }

    @Override
    public ReadModel read(Requester requester, ModelId modelId) {
        ModelQueryService.OpenedModel opened;
        try {
            opened = models.openWithPlacements(editor(requester),
                    uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId.of(modelId.value()));
        } catch (ModelingDomainException hidden) {
            // Нет модели или она скрыта списком доступа — для помощника одно и то же.
            throw AdvisorDomainException.notFound(Message.of(AdvisorMessages.MODEL, modelId));
        }
        ArchitectureModel model = opened.model();
        Map<UUID, String> archiIds = new HashMap<>();
        model.elements().values().forEach(e -> archiIds.put(e.id().value(), e.archiId().value()));
        model.relationships().values().forEach(r -> archiIds.put(r.id().value(), r.archiId().value()));

        List<ContextElement> elements = model.elements().values().stream()
                .sorted(Comparator.comparing((Element e) -> e.sortOrder()).thenComparing(e -> e.archiId().value()))
                .map(ModelingContextReader::element).toList();
        List<ContextRelation> relations = model.relationships().values().stream()
                .sorted(Comparator.comparing((Relationship r) -> r.sortOrder()).thenComparing(r -> r.archiId().value()))
                .map(r -> new ContextRelation(r.archiId().value(), r.archiType().value(),
                        archiIds.get(r.source().value()), archiIds.get(r.target().value())))
                .toList();
        Map<UUID, ViewPlacements> placements = opened.placements().stream()
                .collect(Collectors.toMap(p -> p.viewId().value(), p -> p));
        List<GraphView> views = model.views().stream()
                .sorted(Comparator.comparing(ViewRef::sortOrder))
                .map(v -> new GraphView(ViewId.of(v.id().value()), v.archiId().value(), v.name(),
                        Optional.ofNullable(placements.get(v.id().value())).map(ViewPlacements::elementIds)
                                .orElse(List.of()).stream().map(id -> archiIds.get(id.value())).toList()))
                .toList();
        return new ReadModel(WorkspaceId.of(model.workspaceId().value()), model.status() != ModelStatus.ACTIVE,
                new ModelGraph(elements, relations, views));
    }

    private static ContextElement element(Element element) {
        return new ContextElement(element.archiId().value(), element.archiType().value(), element.name(),
                Optional.ofNullable(element.documentation()), layer(element));
    }

    private static ContextLayer layer(Element element) {
        return switch (element.layer()) {
            case STRATEGY -> ContextLayer.STRATEGY;
            case MOTIVATION -> ContextLayer.MOTIVATION;
            case BUSINESS -> ContextLayer.BUSINESS;
            case APPLICATION -> ContextLayer.APPLICATION;
            case TECHNOLOGY -> ContextLayer.TECHNOLOGY;
            case PHYSICAL -> ContextLayer.PHYSICAL;
            case IMPLEMENTATION -> ContextLayer.IMPLEMENTATION;
            case OTHER -> ContextLayer.OTHER;
        };
    }

    static EditorIdentity editor(Requester requester) {
        return new EditorIdentity(requester.subject(),
                requester.roles().stream().map(r -> Role.valueOf(r.name())).collect(Collectors.toSet()),
                requester.groups());
    }
}
