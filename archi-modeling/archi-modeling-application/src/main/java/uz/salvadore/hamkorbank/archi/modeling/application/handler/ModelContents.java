package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.PropertyValue;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/**
 * Общее у сценариев содержимого модели: чья это модель, удаление концепта вместе с его следами
 * на представлениях, порядок свойств в правке.
 */
public final class ModelContents {

    private final ModelingKernel kernel;

    public ModelContents(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Модель, которой принадлежит элемент или связь; неизвестный ключ — «не найдено». */
    public ModelId ownerOf(UUID objectId) {
        return kernel.models().ownerOf(objectId).orElseThrow(
                () -> ModelingDomainException.notFound(Message.of(ModelingMessages.OBJECT, objectId)));
    }

    /** Удаление элемента; со связями — {@code 409}. Размещения на представлениях уходят вместе с ним. */
    public void removeElement(ArchitectureModel model, ElementId elementId) {
        model.removeElement(elementId, kernel.now());
        for (ViewId viewId : kernel.models().viewsReferencing(elementId)) {
            View view = kernel.views().load(viewId).orElseThrow();
            if (view.removeElementReferences(elementId)) {
                kernel.views().save(view);
            }
        }
    }

    /** Удаление связи снимает её рёбра на представлениях; примыкающая связь — {@code 409}. */
    public void removeRelationship(ArchitectureModel model, RelationshipId relationshipId) {
        model.removeRelationship(relationshipId, kernel.now());
        for (ViewId viewId : kernel.models().viewsReferencing(relationshipId)) {
            View view = kernel.views().load(viewId).orElseThrow();
            if (view.removeRelationshipReferences(relationshipId)) {
                kernel.views().save(view);
            }
        }
    }

    /** Свойства получают разреженный порядок по месту в списке. */
    public static List<PropertyEntry> numbered(List<PropertyValue> values) {
        ArrayList<PropertyEntry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            entries.add(new PropertyEntry(values.get(i).key(), values.get(i).value(), SortOrder.ofPosition(i)));
        }
        return entries;
    }
}
