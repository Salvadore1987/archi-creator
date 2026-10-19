package uz.salvadore.hamkorbank.archi.bootstrap.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CreateRelationshipResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ElementApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.LockApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.RelationshipApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ViewService;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;

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
    ViewService views;
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
        View context = views.create(ARCHITECT, model, "Контекст", null, null);
        View detail = views.create(ARCHITECT, model, "Детали", null, null);
        View empty = views.create(ARCHITECT, model, "Пустое", null, null);
        ElementId componentId = ElementId.of(component.id());
        ElementId actorId = ElementId.of(actor.id());
        ViewNode c = views.place(ARCHITECT, context.id(), componentId, new Bounds(0, 0, 120, 55), null);
        ViewNode a = views.place(ARCHITECT, context.id(), actorId, new Bounds(300, 0, 120, 55), null);
        CreateRelationshipResponse serving = relationships.createRelationship(ARCHITECT,
                new CreateRelationshipCommand(model.value(), ArchiType.ofSimpleName("ServingRelationship").value(),
                        component.id(), actor.id(), null, null, null, null, null, null,
                        new CreateRelationshipCommand.Edge(context.id().value(), c.id().value(), a.id().value(), null,
                                null),
                        null));
        views.place(ARCHITECT, detail.id(), componentId, new Bounds(0, 0, 120, 55), null);

        Map<ViewId, ViewPlacements> placements = unitOfWork.read(() -> viewRepository.placements(model)).stream()
                .collect(Collectors.toMap(ViewPlacements::viewId, Function.identity()));

        assertEquals(Set.of(componentId, actorId), Set.copyOf(placements.get(context.id()).elementIds()));
        assertEquals(List.of(RelationshipId.of(serving.relationship().id())),
                placements.get(context.id()).relationshipIds());
        assertEquals(List.of(componentId), placements.get(detail.id()).elementIds());
        assertEquals(List.of(), placements.get(detail.id()).relationshipIds());
        assertEquals(null, placements.get(empty.id()), "пустое представление адаптер может не вернуть");
        assertEquals(false, placements.values().stream()
                .anyMatch(p -> p.elementIds().contains(ElementId.of(unplaced.id()))));
    }
}
