package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

/** Состояние задания выгрузки; терминальны {@link #DELIVERED} и {@link #FAILED}. */
public enum ExportStatus {
    REQUESTED,
    RENDERED,
    DELIVERED,
    FAILED
}
