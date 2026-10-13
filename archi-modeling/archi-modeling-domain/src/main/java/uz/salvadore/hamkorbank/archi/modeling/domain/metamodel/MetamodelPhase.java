package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

/**
 * Фаза метамодели, в которой тип становится редактируемым.
 * До своей фазы тип хранится и выгружается как есть, но не редактируется.
 */
public enum MetamodelPhase {
    /** Business, Application, Technology, связи и Junction. */
    PHASE_1(true),
    /** Motivation и Strategy. */
    PHASE_2(true),
    /** Physical, Implementation &amp; Migration — пока не поддержана. */
    PHASE_3(false);

    private final boolean supported;

    MetamodelPhase(boolean supported) {
        this.supported = supported;
    }

    /** Редактируются ли типы этой фазы сейчас. */
    public boolean supported() {
        return supported;
    }
}
