package uz.salvadore.hamkorbank.archi.bootstrap.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.AcquireLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateElementCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateModelCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateRelationshipCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateViewCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ElementApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.LockApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.RelationshipApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/** Размещения элементов и связей на представлениях — запросом к таблицам узлов и рёбер. */
class PlacementsIT extends IntegrationTest {

    @Autowired
    ModelApplicationService lifecycle;
    @Autowired
    LockApplicationService locks;
    @Autowired
    ElementApplicationService elements;
    @Autowired
    RelationshipApplicationService relationships;
    @Autowired
    ViewApplicationService views;
    @Autowired
    ViewRepository viewRepository;
    @Autowired
    UnitOfWork unitOfWork;

    @Test
    @DisplayName("FR-31: размещения — элементы с узлом и связи с ребром по представлениям, без повторов")
    void placementsPerView() {
        ModelId model = ModelId.of(lifecycle.createModel(ARCHITECT,
                new CreateModelCommand(workspace().value(), "Размещения", null)).id());
        locks.acquireLock(ARCHITECT, new AcquireLockCommand(model.value()));
        ElementResponse component = elements.createElement(ARCHITECT, new CreateElementCommand(model.value(),
                ArchiType.ofSimpleName("ApplicationComponent").value(), "АБС", null, null, null, null));
        ElementResponse actor = elements.createElement(ARCHITECT, new CreateElementCommand(model.value(),
                ArchiType.ofSimpleName("BusinessActor").value(), "Клиент", null, null, null, null));
        ElementResponse unplaced = elements.createElement(ARCHITECT, new CreateElementCommand(model.value(),
                ArchiType.ofSimpleName("Node").value(), "Сервер", null, null, null, null));
        ViewId context = view(model, "Контекст");
        ViewId detail = view(model, "Детали");
        ViewId empty = view(model, "Пустое");
        ElementId componentId = ElementId.of(component.id());
        ElementId actorId = ElementId.of(actor.id());
        ViewNodeResponse c = place(context, component.id(), 0);
        ViewNodeResponse a = place(context, actor.id(), 300);
        CreateRelationshipResponse serving = relationships.createRelationship(ARCHITECT,
                new CreateRelationshipCommand(model.value(), ArchiType.ofSimpleName("ServingRelationship").value(),
                        component.id(), actor.id(), null, null, null, null, null, null,
                        new CreateRelationshipCommand.Edge(context.value(), c.id(), a.id(), null, null),
                        null));
        place(detail, component.id(), 0);

        Map<ViewId, ViewPlacements> placements = unitOfWork.read(() -> viewRepository.placements(model)).stream()
                .collect(Collectors.toMap(ViewPlacements::viewId, Function.identity()));

        assertEquals(Set.of(componentId, actorId), Set.copyOf(placements.get(context).elementIds()));
        assertEquals(List.of(RelationshipId.of(serving.relationship().id())),
                placements.get(context).relationshipIds());
        assertEquals(List.of(componentId), placements.get(detail).elementIds());
        assertEquals(List.of(), placements.get(detail).relationshipIds());
        assertEquals(null, placements.get(empty), "пустое представление адаптер может не вернуть");
        assertEquals(false, placements.values().stream()
                .anyMatch(p -> p.elementIds().contains(ElementId.of(unplaced.id()))));
    }

    private ViewId view(ModelId model, String name) {
        return ViewId.of(views.createView(ARCHITECT, new CreateViewCommand(model.value(), name, null, null, null,
                null)).id());
    }

    private ViewNodeResponse place(ViewId view, UUID element, int x) {
        return views.placeNode(ARCHITECT, new PlaceViewNodeCommand(view.value(), null, element, null, null, x, 0,
                120, 55, null, null, null)).node();
    }
}
