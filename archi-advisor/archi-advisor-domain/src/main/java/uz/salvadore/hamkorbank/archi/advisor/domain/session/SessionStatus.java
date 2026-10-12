package uz.salvadore.hamkorbank.archi.advisor.domain.session;

/** Состояние сессии помощника. */
public enum SessionStatus {
    REQUESTED,
    STREAMING,
    COMPLETED,
    FAILED,
    REJECTED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == REJECTED;
    }
}
