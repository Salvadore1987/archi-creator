package uz.salvadore.hamkorbank.archi.advisor.domain.context;

import java.util.Objects;
import java.util.Optional;

/** Элемент модели в контексте: тип, имя, документация — без папок, свойств и стиля. */
public record ContextElement(String archiId, String archiType, String name, Optional<String> documentation,
                             ContextLayer layer) {

    public ContextElement {
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(name, "name");
        documentation = Objects.requireNonNullElse(documentation, Optional.<String>empty()).filter(d -> !d.isBlank());
        Objects.requireNonNull(layer, "layer");
    }

    ContextElement withoutDocumentation() {
        return new ContextElement(archiId, archiType, name, Optional.empty(), layer);
    }
}
