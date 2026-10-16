package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;

/** Переход сессии, которого нет в state machine: завершённая сессия не дополняется. */
public final class IllegalSessionTransitionException extends AdvisorDomainException {

    /**
     * Отказ перехода.
     *
     * @param command команда, которую сессия не принимает, — сообщением, чтобы текст переводился
     */
    public IllegalSessionTransitionException(SessionStatus from, Message command) {
        super(AdvisorCodes.SESSION_TRANSITION, Failure.CONFLICT,
                Message.of(AdvisorMessages.SESSION_TRANSITION, from.label(), command),
                Map.of("status", from.name()));
    }
}
