package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Переход, которого автомат сессии не допускает. */
public final class IllegalImportTransitionException extends InterchangeDomainException {

    /** Код нарушенного правила переходов сессии импорта. */
    public static final String INVARIANT = InterchangeCodes.IMPORT_TRANSITION;

    private final ImportStatus from;
    private final Message command;

    IllegalImportTransitionException(ImportStatus from, Message command) {
        super(INVARIANT, Message.of(InterchangeMessages.IMPORT_TRANSITION, command, from.label()));
        this.from = from;
        this.command = command;
    }

    /** Состояние, из которого переход не допускается. */
    public ImportStatus from() {
        return from;
    }

    /** Команда, которую отклонил автомат. */
    public Message command() {
        return command;
    }
}
