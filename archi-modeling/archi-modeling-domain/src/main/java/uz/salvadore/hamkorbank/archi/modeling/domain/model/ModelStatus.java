package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
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

    /** Название состояния для текста пользователю — переводится на границе. */
    public Message label() {
        return Message.of(labelKey);
    }
}
