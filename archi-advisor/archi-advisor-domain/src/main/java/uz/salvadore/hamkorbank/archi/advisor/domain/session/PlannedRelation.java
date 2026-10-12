package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.util.Objects;

/**
 * Предлагаемая связь плана — концы по имени из того же плана.
 *
 * @param permitted {@code false} — связь недопустима по матрице ArchiMate и требует
 *                  внимания; план её не прячет: это предложение, а не команда
 */
public record PlannedRelation(String archiType, String sourceName, String targetName, boolean permitted) {

    public PlannedRelation {
        Objects.requireNonNull(archiType, "archiType");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(targetName, "targetName");
    }
}
