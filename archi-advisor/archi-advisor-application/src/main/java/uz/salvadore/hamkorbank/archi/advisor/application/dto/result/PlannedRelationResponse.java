package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

/**
 * Связь плана.
 *
 * @param permitted допустима ли связь по матрице ArchiMate
 */
public record PlannedRelationResponse(String archiType, String sourceName, String targetName, boolean permitted) {
}
