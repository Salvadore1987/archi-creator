package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;

/** Переход сессии, которого нет в state machine: завершённая сессия не дополняется. */
public final class IllegalSessionTransitionException extends AdvisorDomainException {

    public IllegalSessionTransitionException(SessionStatus from, String transition) {
        super(AdvisorCodes.SESSION_TRANSITION, Failure.CONFLICT,
                Message.of(AdvisorMessages.SESSION_TRANSITION, from.name(), transition),
                Map.of("status", from.name()));
    }
}
