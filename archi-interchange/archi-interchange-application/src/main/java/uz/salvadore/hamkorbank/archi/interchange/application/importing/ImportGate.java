package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Предел одновременных импортов в одно рабочее пространство. Сверх предела — отказ
 * сразу, а не очередь: ждущий импорт держал бы поток запроса, который нужен канве,
 * а разбор большого файла — память. Предел живёт в процессе: приложение — один экземпляр.
 */
final class ImportGate {

    private final int limit;
    private final ConcurrentMap<WorkspaceId, Semaphore> slots = new ConcurrentHashMap<>();

    ImportGate(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException(String.valueOf(limit));
        }
        this.limit = limit;
    }

    /** Выполняет импорт, если в пространстве есть свободное место; иначе — {@code 409}. */
    <T> T through(WorkspaceId workspace, Supplier<T> work) {
        Semaphore slot = slots.computeIfAbsent(workspace, any -> new Semaphore(limit));
        if (!slot.tryAcquire()) {
            throw new InterchangeException(InterchangeCodes.IMPORT_BUSY, Failure.CONFLICT,
                    Message.of(InterchangeMessages.IMPORT_WORKSPACE_BUSY), Map.of());
        }
        try {
            return work.get();
        } finally {
            slot.release();
        }
    }
}
