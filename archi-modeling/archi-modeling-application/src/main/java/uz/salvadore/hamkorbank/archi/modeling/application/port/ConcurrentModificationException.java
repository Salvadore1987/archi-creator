package uz.salvadore.hamkorbank.archi.modeling.application.port;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Строку агрегата успел изменить другой писатель: правки не перезаписываются,
 * клиент перечитывает состояние и повторяет. Код — тот же, что у записи без блокировки:
 * для пользователя это одна и та же ситуация «модель правит кто-то ещё».
 *
 * @param what что изменено — сообщение с подписью объекта
 */
public final class ConcurrentModificationException extends ModelingException {

    public ConcurrentModificationException(Message what) {
        super(ModelingCodes.LOCK_REQUIRED, Failure.CONFLICT, Message.of(ModelingMessages.CONCURRENT_MODIFICATION, what));
    }
}
