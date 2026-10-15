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

    /** Сообщение с ключом, которое переводится на язык запроса. */
    public Message reason() {
        return reason;
    }

    /** Код нарушенного правила выгрузки. */
    public String invariant() {
        return invariant;
    }
}
