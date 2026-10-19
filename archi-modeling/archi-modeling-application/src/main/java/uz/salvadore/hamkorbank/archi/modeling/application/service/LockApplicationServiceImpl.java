package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.AcquireLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReleaseLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetLockQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LockResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.LockAcquireCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.LockGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.LockReleaseCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.LockApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Блокировки модели: делегирует в handler'ы сценариев. */
public final class LockApplicationServiceImpl implements LockApplicationService {

    private final LockAcquireCommandHandler acquire;
    private final LockReleaseCommandHandler release;
    private final LockGetQueryHandler get;

    public LockApplicationServiceImpl(LockAcquireCommandHandler acquire, LockReleaseCommandHandler release,
                                      LockGetQueryHandler get) {
        this.acquire = acquire;
        this.release = release;
        this.get = get;
    }

    @Override
    public LockResponse acquireLock(EditorIdentity actor, AcquireLockCommand command) {
        return acquire.handle(actor, command);
    }

    @Override
    public void releaseLock(EditorIdentity actor, ReleaseLockCommand command) {
        release.handle(actor, command);
    }

    @Override
    public Optional<LockResponse> getLock(EditorIdentity actor, GetLockQuery query) {
        return get.handle(actor, query);
    }
}
