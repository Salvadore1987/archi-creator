package uz.salvadore.hamkorbank.archi.bootstrap.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uz.salvadore.hamkorbank.archi.bootstrap.support.IntegrationTest;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewPlacements;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ElementService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.LockService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelLifecycleService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.RelationshipService;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ViewService;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;

/** Размещения элементов и связей на представлениях — запросом к таблицам узлов и рёбер. */
class PlacementsIT extends IntegrationTest {

    @Autowired
    ModelLifecycleService lifecycle;
    @Autowired
    LockService locks;
    @Autowired
    ElementService elements;
    @Autowired
    RelationshipService relationships;
    @Autowired
    ViewService views;
    @Autowired
    ViewRepository viewRepository;
    @Autowired
    UnitOfWork unitOfWork;

    @Test
    @DisplayName("FR-31: размещения — элементы с узлом и связи с ребром по представлениям, без повторов")
    void placementsPerView() {
        ModelId model = lifecycle.create(ARCHITECT, workspace(), "Размещения", Optional.empty()).id();
        locks.acquire(ARCHITECT, model);
        Element component = elements.create(ARCHITECT, model, ArchiType.ofSimpleName("ApplicationComponent"), "АБС",
                Optional.empty(), Optional.empty());
        Element actor = elements.create(ARCHITECT, model, ArchiType.ofSimpleName("BusinessActor"), "Клиент",
                Optional.empty(), Optional.empty());
        Element unplaced = elements.create(ARCHITECT, model, ArchiType.ofSimpleName("Node"), "Сервер",
                Optional.empty(), Optional.empty());
        View context = views.create(ARCHITECT, model, "Контекст", Optional.empty(), Optional.empty());
        View detail = views.create(ARCHITECT, model, "Детали", Optional.empty(), Optional.empty());
        View empty = views.create(ARCHITECT, model, "Пустое", Optional.empty(), Optional.empty());
        ViewNode c = views.place(ARCHITECT, context.id(), component.id(), new Bounds(0, 0, 120, 55), Optional.empty());
        ViewNode a = views.place(ARCHITECT, context.id(), actor.id(), new Bounds(300, 0, 120, 55), Optional.empty());
        var serving = relationships.create(ARCHITECT, model, ArchiType.ofSimpleName("ServingRelationship"),
                component.id().value(), actor.id().value(), Optional.empty(),
                Optional.of(new RelationshipService.EdgePlacement(context.id(), c.id().value(), a.id().value())),
                Optional.empty());
        views.place(ARCHITECT, detail.id(), component.id(), new Bounds(0, 0, 120, 55), Optional.empty());

        Map<ViewId, ViewPlacements> placements = unitOfWork.read(() -> viewRepository.placements(model)).stream()
                .collect(Collectors.toMap(ViewPlacements::viewId, Function.identity()));

        assertEquals(Set.of(component.id(), actor.id()), Set.copyOf(placements.get(context.id()).elementIds()));
        assertEquals(List.of(serving.relationship().id()), placements.get(context.id()).relationshipIds());
        assertEquals(List.of(component.id()), placements.get(detail.id()).elementIds());
        assertEquals(List.of(), placements.get(detail.id()).relationshipIds());
        assertEquals(null, placements.get(empty.id()), "пустое представление адаптер может не вернуть");
        assertEquals(false, placements.values().stream().anyMatch(p -> p.elementIds().contains(unplaced.id())));
    }
}
