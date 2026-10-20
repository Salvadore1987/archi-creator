package uz.salvadore.hamkorbank.archi.modeling.application.port;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Снимок не раскладывается в строки модели. Для отката к версии это дефект сервера — испорчен
 * собственный снимок, и отказ так и уходит клиенту. Кто передал содержимое извне, переводит отказ
 * в свой: для него это повреждённый документ. Причина — исключение того, кто раскладывал.
 */
public final class SnapshotUnreadableException extends ModelingDomainException {

    public SnapshotUnreadableException(RuntimeException cause) {
        super(ModelingCodes.SNAPSHOT_CORRUPTED, Failure.DEFECT, Message.of(ModelingMessages.SNAPSHOT_CORRUPTED));
        initCause(cause);
    }
}
