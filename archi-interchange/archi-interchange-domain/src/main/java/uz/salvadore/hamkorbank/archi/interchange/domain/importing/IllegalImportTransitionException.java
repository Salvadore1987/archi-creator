package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

/** Переход, которого автомат сессии не допускает. */
public final class IllegalImportTransitionException extends IllegalStateException {

    public static final String INVARIANT = "INV-IXC-002";

    private final ImportStatus from;
    private final String command;

    IllegalImportTransitionException(ImportStatus from, String command) {
        super(INVARIANT + ": " + command + " недопустим из состояния " + from);
        this.from = from;
        this.command = command;
    }

    public ImportStatus from() {
        return from;
    }

    public String command() {
        return command;
    }
}
