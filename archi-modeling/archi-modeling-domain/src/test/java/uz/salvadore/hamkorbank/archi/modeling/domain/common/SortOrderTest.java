package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;

class SortOrderTest {

    @Test
    @DisplayName("INV-MDL-005: вставка не перенумеровывает соседей")
    void insertDoesNotRenumberSiblings() {
        ArchitectureModel model = Models.empty();
        Element first = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "АБС");
        Element second = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "CRM");
        model.elements().markPersisted();

        Element third = Models.element(model, "ApplicationComponent", FolderType.APPLICATION, "Шлюз");

        assertEquals(List.of(1000L, 2000L, 3000L), List.of(first.sortOrder().value(), second.sortOrder().value(),
                third.sortOrder().value()));
        Map<ElementId, Element> touched = model.elements().upserts();
        assertEquals(1, touched.size(), "в изменения попал только новый элемент, соседи не тронуты");
        assertTrue(touched.containsKey(third.id()));
    }

    @Test
    @DisplayName("INV-MDL-005: середина зазора, а исчерпанный зазор — сигнал перебалансировки")
    void betweenTakesMiddleOfGap() {
        assertEquals(Optional.of(SortOrder.of(1500)), SortOrder.between(SortOrder.of(1000), SortOrder.of(2000)));
        assertEquals(Optional.of(SortOrder.of(500)), SortOrder.between(null, SortOrder.of(1000)));
        assertEquals(Optional.empty(), SortOrder.between(SortOrder.of(1000), SortOrder.of(1001)));
        assertEquals(SortOrder.of(3000), SortOrder.afterLast(List.of(SortOrder.of(1000), SortOrder.of(2500))));
        assertEquals(SortOrder.of(1000), SortOrder.afterLast(List.of()));
        assertEquals("1000,2000,3000", List.of(0, 1, 2).stream().map(SortOrder::ofPosition)
                .map(s -> String.valueOf(s.value())).collect(Collectors.joining(",")));
    }
}
