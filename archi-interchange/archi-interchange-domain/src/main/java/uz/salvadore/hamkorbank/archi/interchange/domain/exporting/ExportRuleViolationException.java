package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

/** Нарушение правила выгрузки; {@link #invariant()} — код из invariants.md. */
public final class ExportRuleViolationException extends IllegalStateException {

    private final String invariant;

    ExportRuleViolationException(String invariant, String message) {
        super(invariant + ": " + message);
        this.invariant = invariant;
    }

    public String invariant() {
        return invariant;
    }
}
