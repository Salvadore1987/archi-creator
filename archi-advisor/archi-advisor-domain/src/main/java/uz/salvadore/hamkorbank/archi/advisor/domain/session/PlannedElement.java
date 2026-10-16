package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.util.Objects;

/**
 * Предлагаемый элемент плана — тип и имя, без идентификатора: создавать его
 * будет архитектор, и идентификатор появится тогда.
 *
 * @param existingRef такой элемент в модели уже есть — план на него ссылается; {@code null} — элемент новый
 */
public record PlannedElement(String archiType, String name, ElementRef existingRef) {

    public PlannedElement {
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(name, "name");
    }
}
