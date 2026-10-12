package uz.salvadore.hamkorbank.archi.advisor.domain.context;

import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;

/** Представление в графе модели — с внутренним ключом, по которому его выбирает запрос. */
public record GraphView(ViewId id, String archiId, String name, List<String> elementArchiIds) {

    public GraphView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(name, "name");
        elementArchiIds = List.copyOf(elementArchiIds);
    }

    ContextView toContext() {
        return new ContextView(archiId, name, elementArchiIds);
    }
}
