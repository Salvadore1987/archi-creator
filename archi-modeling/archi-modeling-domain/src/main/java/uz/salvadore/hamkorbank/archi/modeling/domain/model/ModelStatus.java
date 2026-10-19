package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/** Жизненный цикл модели: {@code ACTIVE ⇄ DELETED → PURGED}. */
public enum ModelStatus {
    /** Модель открыта для правки. */
    ACTIVE(ModelingMessages.STATUS_ACTIVE),
    /** Модель удалена, её можно восстановить. */
    DELETED(ModelingMessages.STATUS_DELETED),
    /** Модель уничтожена окончательно. */
    PURGED(ModelingMessages.STATUS_PURGED);

    private final String labelKey;

    ModelStatus(String labelKey) {
        this.labelKey = labelKey;
    }

    /** Содержимое правят только у активной модели; иначе — отказ жизненного цикла. */
    public void requireActive() {
        if (this != ACTIVE) {
            throw new ModelingDomainException(ModelingCodes.LIFECYCLE, Failure.CONFLICT,
                    Message.of(ModelingMessages.MODEL_NOT_ACTIVE, label()));
        }
    }

    /** Выгружают и читают для замены только активную модель; удалённая — отказ «модель удалена». */
    public void requireExportable() {
        if (this != ACTIVE) {
            throw new ModelingDomainException(ModelingCodes.MODEL_DELETED, Failure.CONFLICT,
                    Message.of(ModelingMessages.MODEL_DELETED));
        }
    }

    /** Название состояния для текста пользователю — переводится на границе. */
    public Message label() {
        return Message.of(labelKey);
    }
}
