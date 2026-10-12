package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.util.Objects;
import java.util.Optional;

/**
 * Предлагаемый элемент плана — тип и имя, без идентификатора: создавать его
 * будет архитектор, и идентификатор появится тогда.
 *
 * @param existingRef такой элемент в модели уже есть — план на него ссылается
 */
public record PlannedElement(String archiType, String name, Optional<ElementRef> existingRef) {

    public PlannedElement {
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(name, "name");
        existingRef = Objects.requireNonNullElse(existingRef, Optional.empty());
    }
}
