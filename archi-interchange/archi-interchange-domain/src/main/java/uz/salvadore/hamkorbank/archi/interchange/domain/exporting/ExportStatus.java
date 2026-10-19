package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Состояние задания выгрузки; терминальны {@link #DELIVERED} и {@link #FAILED}. */
public enum ExportStatus {
    REQUESTED(InterchangeMessages.STATUS_EXPORT_REQUESTED),
    RENDERED(InterchangeMessages.STATUS_EXPORT_RENDERED),
    DELIVERED(InterchangeMessages.STATUS_EXPORT_DELIVERED),
    FAILED(InterchangeMessages.STATUS_EXPORT_FAILED);

    private final String labelKey;

    ExportStatus(String labelKey) {
        this.labelKey = labelKey;
    }

    /** Название состояния для текста пользователю — переводится на границе. */
    public Message label() {
        return Message.of(labelKey);
    }
}
