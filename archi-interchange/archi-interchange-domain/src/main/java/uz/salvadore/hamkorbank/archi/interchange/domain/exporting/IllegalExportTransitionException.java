package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Задание выгрузки получило команду, недопустимую из его состояния. */
public final class IllegalExportTransitionException extends InterchangeDomainException {

    IllegalExportTransitionException(Message reason) {
        super(InterchangeCodes.EXPORT_TRANSITION, reason);
    }
}
