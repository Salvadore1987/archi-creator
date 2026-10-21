package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AccessPolicy;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AdvisorOperation;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorEvents;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AdvisorMetrics;
import uz.salvadore.hamkorbank.archi.advisor.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.UuidV7;

/**
 * Общее у сценариев помощника: роль, метрика, транзакция, события, часы и ключи. Открыто только
 * handler'ам: адаптеры зовут сценарии через входные порты.
 *
 * <p>Роль проверяется первой — раньше идемпотентности и чтения модели, и отказ любого вида
 * попадает в метрику сценария.
 */
public final class AdvisorKernel {

    private final UnitOfWork unitOfWork;
    private final AdvisorEvents events;
    private final AdvisorMetrics metrics;
    private final Clock clock;
    private final UuidV7 uuids;

    public AdvisorKernel(UnitOfWork unitOfWork, AdvisorEvents events, AdvisorMetrics metrics, Clock clock) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.events = Objects.requireNonNull(events);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.uuids = new UuidV7(clock);
    }

    /** Сценарий под метрикой операции; роль актора проверяется до работы. */
    <T> T run(AdvisorOperation operation, Requester actor, Supplier<T> work) {
        return metrics.observe(operation.useCase(), () -> {
            AccessPolicy.require(actor, operation);
            return work.get();
        });
    }

    /** Транзакции сценария. */
    UnitOfWork unitOfWork() {
        return unitOfWork;
    }

    /** Публикация событий после коммита. */
    AdvisorEvents events() {
        return events;
    }

    /** Метрики помощника сверх длительности сценария. */
    AdvisorMetrics metrics() {
        return metrics;
    }

    /** Текущий момент по часам приложения. */
    Instant now() {
        return clock.instant();
    }

    /** Генератор ключей версии 7 по часам приложения. */
    UuidV7 uuids() {
        return uuids;
    }
}
