package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Переход, которого автомат сессии не допускает. */
public final class IllegalImportTransitionException extends IllegalStateException {

    public static final String INVARIANT = InterchangeCodes.IMPORT_TRANSITION;

    private final ImportStatus from;
    private final String command;

    IllegalImportTransitionException(ImportStatus from, String command) {
        super(INVARIANT + ": " + Message.of(InterchangeMessages.IMPORT_TRANSITION, command, from));
        this.from = from;
        this.command = command;
    }

    public Message reason() {
        return Message.of(InterchangeMessages.IMPORT_TRANSITION, command, from);
    }

    public ImportStatus from() {
        return from;
    }

    public String command() {
        return command;
    }
}
