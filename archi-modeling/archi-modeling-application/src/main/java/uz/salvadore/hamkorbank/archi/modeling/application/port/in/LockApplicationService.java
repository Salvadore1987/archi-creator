package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.AcquireLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReleaseLockCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetLockQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LockResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Блокировка модели на запись: захват, снятие, текущее состояние. */
public interface LockApplicationService {

    /** Захват или продление своей блокировки; чужая действующая — {@code 409} с владельцем. */
    LockResponse acquireLock(EditorIdentity actor, AcquireLockCommand command);

    /** Владелец снимает свою, {@code ADMIN} — любую; нет блокировки — делать нечего. */
    void releaseLock(EditorIdentity actor, ReleaseLockCommand command);

    /** Действующая блокировка — чтобы остальные видели модель только для чтения; нет — пусто. */
    Optional<LockResponse> getLock(EditorIdentity actor, GetLockQuery query);
}
