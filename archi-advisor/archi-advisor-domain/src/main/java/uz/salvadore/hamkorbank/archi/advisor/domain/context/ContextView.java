package uz.salvadore.hamkorbank.archi.advisor.domain.context;

import java.util.List;
import java.util.Objects;

/** Представление без геометрии: что на нём размещено, а не где. */
public record ContextView(String archiId, String name, List<String> elementArchiIds) {

    public ContextView {
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(name, "name");
        elementArchiIds = List.copyOf(elementArchiIds);
    }
}
