package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;

/** Состояние сессии помощника. */
public enum SessionStatus {
    REQUESTED(AdvisorMessages.STATUS_REQUESTED),
    STREAMING(AdvisorMessages.STATUS_STREAMING),
    COMPLETED(AdvisorMessages.STATUS_COMPLETED),
    FAILED(AdvisorMessages.STATUS_FAILED),
    REJECTED(AdvisorMessages.STATUS_REJECTED);

    private final String labelKey;

    SessionStatus(String labelKey) {
        this.labelKey = labelKey;
    }

    /** Конечное ли состояние: из него переходов нет. */
    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == REJECTED;
    }

    /** Название состояния для человека — ключом, а не именем константы. */
    public Message label() {
        return Message.of(labelKey);
    }
}
