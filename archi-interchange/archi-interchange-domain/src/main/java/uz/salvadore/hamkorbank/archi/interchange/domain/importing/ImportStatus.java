package uz.salvadore.hamkorbank.archi.interchange.domain.importing;

/** Состояние сессии импорта; терминальны {@link #APPLIED} и {@link #REJECTED}. */
public enum ImportStatus {
    RECEIVED,
    PARSED,
    VALIDATED,
    APPLIED,
    REJECTED;

    public boolean terminal() {
        return this == APPLIED || this == REJECTED;
    }
}
