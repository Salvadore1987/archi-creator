package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportLoss;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossEntry;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.LossEntryId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Общее у сценариев interchange: роль, метрика, транзакция, часы и ключи. Открыто только
 * handler'ам: адаптеры зовут сценарии через входные порты.
 *
 * <p>Роль проверяется первой — раньше разбора ввода и чтения модели: без роли клиент получает
 * {@code 403}, а не отказ в значении параметра, и отказ любого вида попадает в метрику сценария.
 */
public final class InterchangeKernel {

    private final UnitOfWork unitOfWork;
    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final TextCatalog texts;
    private final Clock clock;
    private final UuidV7 uuids;

    public InterchangeKernel(UnitOfWork unitOfWork, InterchangeEvents events, InterchangeMetrics metrics,
                             TextCatalog texts, Clock clock) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.events = Objects.requireNonNull(events);
        this.metrics = Objects.requireNonNull(metrics);
        this.texts = Objects.requireNonNull(texts);
        this.clock = Objects.requireNonNull(clock);
        this.uuids = new UuidV7(clock);
    }

    /** Сценарий под метрикой операции; роль актора проверяется до работы. */
    <T> T run(InterchangeOperation operation, EditorIdentity actor, Supplier<T> work) {
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            return work.get();
        });
    }

    /** Транзакции сценария — общие с modeling. */
    UnitOfWork unitOfWork() {
        return unitOfWork;
    }

    /** Публикация событий после коммита. */
    InterchangeEvents events() {
        return events;
    }

    /** Метрики interchange сверх длительности сценария. */
    InterchangeMetrics metrics() {
        return metrics;
    }

    /** Тексты на языке запроса — для того, что сервер отдаёт или сохраняет как данные. */
    TextCatalog texts() {
        return texts;
    }

    /** Часы приложения. */
    Clock clock() {
        return clock;
    }

    /** Текущий момент по часам приложения. */
    Instant now() {
        return clock.instant();
    }

    /** Генератор ключей версии 7 по часам приложения. */
    UuidV7 uuids() {
        return uuids;
    }

    /** Потери выгрузки — записи отчёта с причинами на языке запроса. */
    List<LossEntry> lossEntries(List<ExportLoss> losses) {
        return losses.stream().map(l -> new LossEntry(LossEntryId.next(uuids), ArchiId.of(l.archiId()),
                l.objectKind(), l.aspect(), texts.text(l.reason()))).toList();
    }
}
