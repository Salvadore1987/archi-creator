package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Нарушение правила выгрузки; {@link #invariant()} — код инварианта, {@link #reason()} — сообщение с ключом. */
public final class ExportRuleViolationException extends InterchangeDomainException {


    ExportRuleViolationException(String invariant, Message reason) {
        super(invariant, reason);
    }

    /** Код нарушенного правила выгрузки. */
    public String invariant() {
        return code();
    }
}
