package uz.salvadore.hamkorbank.archi.advisor.domain.session;

/** Состояние сессии помощника. */
public enum SessionStatus {
    REQUESTED,
    STREAMING,
    COMPLETED,
    FAILED,
    REJECTED;

    /** Конечное ли состояние: из него переходов нет. */
    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == REJECTED;
    }
}
