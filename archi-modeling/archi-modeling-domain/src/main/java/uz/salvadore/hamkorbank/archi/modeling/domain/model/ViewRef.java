package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.DiagramType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/**
 * Представление глазами дерева модели: где лежит и как называется. Само представление —
 * отдельный агрегат и грузится по требованию (docs/database.md §4.3); дереву нужно
 * лишь знать, что папка не пуста, и не занять его место в нумерации.
 */
public record ViewRef(ViewId id, FolderId folderId, ArchiId archiId, DiagramType archiType, String name,
                      SortOrder sortOrder) {

    public ViewRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(folderId, "folderId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sortOrder, "sortOrder");
    }
}
