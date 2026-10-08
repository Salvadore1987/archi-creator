package uz.salvadore.hamkorbank.archi.modeling.application.port;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;

/**
 * Строку агрегата успел изменить другой писатель: правки не перезаписываются,
 * клиент перечитывает состояние и повторяет. Код — тот же, что у записи без блокировки:
 * для пользователя это одна и та же ситуация «модель правит кто-то ещё».
 */
public final class ConcurrentModificationException extends ModelingException {

    public ConcurrentModificationException(String what) {
        super("INV-MDL-006", Failure.CONFLICT, what + " изменён(а) параллельно: перечитайте и повторите");
    }
}
