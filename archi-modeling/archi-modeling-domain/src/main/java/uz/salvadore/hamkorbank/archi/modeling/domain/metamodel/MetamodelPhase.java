package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

/**
 * Фаза метамодели, в которой тип становится редактируемым.
 * До своей фазы тип хранится и выгружается как есть, но не редактируется.
 */
public enum MetamodelPhase {
    /** Business, Application, Technology, связи и Junction — этап 1. */
    PHASE_1,
    /** Motivation и Strategy — этап 6. */
    PHASE_2,
    /** Physical, Implementation &amp; Migration. */
    PHASE_3
}
