package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

/**
 * Фаза метамодели, в которой тип становится редактируемым (FR-07, FR-08).
 * До своей фазы тип хранится и выгружается как есть (FR-03), но не редактируется.
 */
public enum MetamodelPhase {
    /** Business, Application, Technology, связи и Junction — этап 1. */
    PHASE_1,
    /** Motivation и Strategy — этап 6. */
    PHASE_2,
    /** Physical, Implementation &amp; Migration. */
    PHASE_3
}
