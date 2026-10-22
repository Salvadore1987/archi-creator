package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** План фрагмента архитектуры: описание, а не изменение модели. */
public record ModelPlanResponse(List<PlannedElementResponse> elements, List<PlannedRelationResponse> relationships,
                                @Nullable String notes) {
}
