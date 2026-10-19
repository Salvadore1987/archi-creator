package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

/** Очистка снимков версий по правилу ретеншена — системный проход по расписанию. */
public interface SnapshotRetentionApplicationService {

    /** Сколько снимков очищено. */
    int purgeSnapshots();
}
