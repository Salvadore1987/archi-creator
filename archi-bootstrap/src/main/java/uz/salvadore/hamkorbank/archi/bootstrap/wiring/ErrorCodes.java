package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;

/** Код отказа для label {@code error_code}: код инварианта, а не имя класса исключения. */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    /** Код отказа исключения контекста; у прочих исключений — простое имя класса. */
    public static String of(Throwable failure) {
        return switch (failure) {
            case ModelingDomainException m -> m.code();
            case InterchangeException i -> i.code();
            case AdvisorDomainException a -> a.code();
            default -> failure.getClass().getSimpleName();
        };
    }
}
