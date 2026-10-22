package uz.salvadore.hamkorbank.archi.advisor.domain.context;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Элемент модели в контексте: тип, имя, документация — без папок, свойств и стиля.
 *
 * @param documentation документация; {@code null} — нет, пустая приводится к {@code null}
 */
public record ContextElement(String archiId, String archiType, String name, @Nullable String documentation,
                             ContextLayer layer) {

    public ContextElement {
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(name, "name");
        if (documentation != null && documentation.isBlank()) {
            documentation = null;
        }
        Objects.requireNonNull(layer, "layer");
    }

    ContextElement withoutDocumentation() {
        return new ContextElement(archiId, archiType, name, null, layer);
    }
}
