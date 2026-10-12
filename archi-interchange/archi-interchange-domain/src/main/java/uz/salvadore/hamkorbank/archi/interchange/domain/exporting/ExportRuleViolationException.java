package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Нарушение правила выгрузки; {@link #invariant()} — код инварианта, {@link #reason()} — сообщение с ключом. */
public final class ExportRuleViolationException extends IllegalStateException {

    private final String invariant;
    private final Message reason;

    ExportRuleViolationException(String invariant, Message reason) {
        super(invariant + ": " + reason);
        this.invariant = invariant;
        this.reason = reason;
    }

    public Message reason() {
        return reason;
    }

    public String invariant() {
        return invariant;
    }
}
