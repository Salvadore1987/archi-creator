package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.List;

/** Дерево модели: папки, элементы, связи, представления — в порядке дерева — и что где размещено. */
public record ModelTreeResponse(ModelSummaryResponse model, List<FolderResponse> folders,
                                List<ElementResponse> elements, List<RelationshipResponse> relationships,
                                List<ViewSummaryResponse> views, List<PlacementResponse> placements) {

    public ModelTreeResponse {
        folders = List.copyOf(folders);
        elements = List.copyOf(elements);
        relationships = List.copyOf(relationships);
        views = List.copyOf(views);
        placements = List.copyOf(placements);
    }
}
