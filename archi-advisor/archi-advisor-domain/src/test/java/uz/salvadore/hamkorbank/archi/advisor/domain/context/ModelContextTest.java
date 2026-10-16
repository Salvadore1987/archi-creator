package uz.salvadore.hamkorbank.archi.advisor.domain.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;

class ModelContextTest {

    private static final ViewId VIEW = ViewId.of(UUIDUtil.uuid("00000000-0000-7000-8000-000000000001"));

    /** Цепочка бизнес → приложения → технологии, по десять элементов в слое. */
    private static ModelGraph layeredGraph() {
        List<ContextElement> elements = new ArrayList<>();
        List<ContextRelation> relations = new ArrayList<>();
        ContextLayer[] layers = {ContextLayer.BUSINESS, ContextLayer.APPLICATION, ContextLayer.TECHNOLOGY};
        for (ContextLayer layer : layers) {
            for (int i = 0; i < 10; i++) {
                elements.add(new ContextElement(layer.name() + "-" + i, "archimate:" + layer.name(), "name " + i,
                        "documentation ".repeat(20), layer));
            }
        }
        for (int i = 0; i < 10; i++) {
            relations.add(new ContextRelation("r-ba-" + i, "archimate:ServingRelationship", "APPLICATION-" + i,
                    "BUSINESS-" + i));
            relations.add(new ContextRelation("r-ta-" + i, "archimate:ServingRelationship", "TECHNOLOGY-" + i,
                    "APPLICATION-" + i));
        }
        List<String> placed = elements.stream().limit(15).map(ContextElement::archiId).toList();
        return new ModelGraph(elements, relations, List.of(new GraphView(VIEW, "view-1", "Обзор", placed)));
    }

    @Test
    @DisplayName("INV-ADV-007: контекст сверх предела усекается и помечается")
    void oversizedContextIsTruncatedAndFlagged() {
        ModelContext full = layeredGraph().whole(AdvisorFunction.REVIEW);
        assertFalse(full.truncated());

        int structureOnly = ModelContext.estimate(
                full.elements().stream().map(ContextElement::withoutDocumentation).toList(),
                full.relationships(), full.views());

        ModelContext fitted = full.fit(structureOnly / 2).orElseThrow();

        assertTrue(fitted.truncated(), "усечение видно в контексте");
        assertTrue(fitted.tokenEstimate() <= structureOnly / 2);
        assertEquals(30, fitted.scopeSize());
        assertTrue(fitted.coverage() < 1.0);
        Set<ContextLayer> kept = fitted.elements().stream().map(ContextElement::layer).collect(Collectors.toSet());
        assertTrue(kept.contains(ContextLayer.BUSINESS), "бизнес-слой уходит последним");
        assertFalse(kept.contains(ContextLayer.TECHNOLOGY), "технологии уходят первыми");
        Set<String> ids = fitted.elements().stream().map(ContextElement::archiId).collect(Collectors.toSet());
        assertTrue(fitted.relationships().stream()
                        .allMatch(r -> ids.contains(r.sourceArchiId()) && ids.contains(r.targetArchiId())),
                "связь без конца в контексте не остаётся");
    }

    @Test
    @DisplayName("INV-ADV-007: первой при усечении уходит документация, структура остаётся")
    void truncatedContextIsFlagged() {
        ModelContext full = layeredGraph().whole(AdvisorFunction.REVIEW);
        int structureOnly = ModelContext.estimate(
                full.elements().stream().map(ContextElement::withoutDocumentation).toList(),
                full.relationships(), full.views());

        ModelContext fitted = full.fit(structureOnly).orElseThrow();

        assertTrue(fitted.truncated());
        assertEquals(30, fitted.elements().size());
        assertTrue(fitted.elements().stream().allMatch(e -> e.documentation() == null));
    }

    @Test
    @DisplayName("Контекст в пределах лимита не меняется и не помечается")
    void fittingContextIsUntouched() {
        ModelContext full = layeredGraph().whole(AdvisorFunction.REVIEW);

        assertEquals(Optional.of(full), full.fit(full.tokenEstimate()));
    }

    @Test
    @DisplayName("INV-ADV-007: не помещается и без элементов — пусто, а не контекст сверх предела")
    void contextOverLimitWithoutElementsIsEmpty() {
        List<ContextView> views = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            views.add(new ContextView("id-v" + i, "Представление " + i, List.of()));
        }
        ModelContext onlyViews = ModelContext.of(AdvisorFunction.REVIEW, List.of(), List.of(), views);
        ModelContext withElement = ModelContext.of(AdvisorFunction.REVIEW,
                List.of(new ContextElement("id-e1", "BusinessActor", "A", null, ContextLayer.BUSINESS)),
                List.of(), views);

        assertEquals(Optional.empty(), onlyViews.fit(1000));
        assertEquals(Optional.empty(), withElement.fit(1000));
    }

    @Test
    @DisplayName("Подсказке — элемент и соседи на заданное число шагов")
    void neighbourhoodIsBoundedByHops() {
        ModelGraph graph = layeredGraph();

        ModelContext oneHop = graph.around(AdvisorFunction.EXPLAIN, "APPLICATION-3", 1).orElseThrow();
        assertEquals(Set.of("APPLICATION-3", "BUSINESS-3", "TECHNOLOGY-3"),
                oneHop.elements().stream().map(ContextElement::archiId).collect(Collectors.toSet()));
        assertEquals(2, oneHop.relationships().size());

        ModelContext twoHops = graph.around(AdvisorFunction.EXPLAIN, "BUSINESS-3", 2).orElseThrow();
        assertEquals(3, twoHops.elements().size());
        assertTrue(graph.around(AdvisorFunction.EXPLAIN, "missing", 2).isEmpty());
    }

    @Test
    @DisplayName("Ревью представления — его элементы и связи между ними")
    void viewScopeKeepsPlacedElementsOnly() {
        ModelContext view = layeredGraph().ofView(AdvisorFunction.REVIEW, VIEW).orElseThrow();

        assertEquals(15, view.elements().size());
        assertTrue(view.relationships().stream().allMatch(r -> r.archiId().startsWith("r-ba-")));
        assertEquals(1, view.views().size());
        assertTrue(view.names().containsKey("view-1"));
        assertEquals("Обзор", view.names().get("view-1"));
    }

    @Test
    @DisplayName("Плану — каталог имён и типов без связей и документации")
    void catalogHasNamesOnly() {
        ModelContext catalog = layeredGraph().catalog(AdvisorFunction.PLAN);

        assertEquals(30, catalog.elements().size());
        assertTrue(catalog.relationships().isEmpty());
        assertTrue(catalog.elements().stream().allMatch(e -> e.documentation() == null));
    }
}
