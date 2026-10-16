package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Состояние сессии импорта; терминальны {@link #APPLIED} и {@link #REJECTED}. */
public enum ImportStatus {
    RECEIVED(InterchangeMessages.STATUS_IMPORT_RECEIVED),
    PARSED(InterchangeMessages.STATUS_IMPORT_PARSED),
    VALIDATED(InterchangeMessages.STATUS_IMPORT_VALIDATED),
    APPLIED(InterchangeMessages.STATUS_IMPORT_APPLIED),
    REJECTED(InterchangeMessages.STATUS_IMPORT_REJECTED);

    private final String labelKey;

    ImportStatus(String labelKey) {
        this.labelKey = labelKey;
    }

    /** Название состояния для текста пользователю — переводится на границе. */
    public Message label() {
        return Message.of(labelKey);
    }

    /** Сессия завершена: дальше переходов нет. */
    public boolean terminal() {
        return this == APPLIED || this == REJECTED;
    }
}
