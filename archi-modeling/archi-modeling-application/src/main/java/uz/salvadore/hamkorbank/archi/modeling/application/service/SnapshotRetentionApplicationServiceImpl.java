package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.handler.SnapshotPurgeCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.SnapshotRetentionApplicationService;

/** Ретеншен снимков: делегирует в handler прохода. */
public final class SnapshotRetentionApplicationServiceImpl implements SnapshotRetentionApplicationService {

    private final SnapshotPurgeCommandHandler purge;

    public SnapshotRetentionApplicationServiceImpl(SnapshotPurgeCommandHandler purge) {
        this.purge = purge;
    }

    @Override
    public int purgeSnapshots() {
        return purge.handle();
    }
}
