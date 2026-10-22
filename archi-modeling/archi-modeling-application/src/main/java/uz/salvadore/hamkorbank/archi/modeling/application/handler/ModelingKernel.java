package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.modeling.application.access.AccessPolicy;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.port.DomainEventPublisher;
import uz.salvadore.hamkorbank.archi.modeling.application.port.IdempotencyRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelAccessListRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelLockRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelVersionRepository;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UseCaseMetrics;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiIdGenerator;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotencyRecord;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;

/**
 * Общее у всех сценариев modeling: зависимости и порядок проверок записи. Открыто только
 * handler'ам: адаптеры и другие контексты зовут сценарии через входные порты.
 *
 * <p>Порядок не случаен: роль (дёшево, без базы) → модель и список доступа (скрытая модель
 * отвечает {@code 404} раньше, чем выдаст себя чем-нибудь ещё) → состояние модели →
 * блокировка автора. Так клиент получает самый осмысленный отказ из возможных.
 */
public final class ModelingKernel {

    private final ModelRepository models;
    private final ViewRepository views;
    private final ModelLockRepository locks;
    private final ModelVersionRepository versions;
    private final ModelAccessListRepository accessLists;
    private final IdempotencyRepository idempotency;
    private final UnitOfWork unitOfWork;
    private final DomainEventPublisher events;
    private final UseCaseMetrics metrics;
    private final TextCatalog texts;
    private final Clock clock;
    private final UuidV7 uuids;
    private final ArchiIdGenerator archiIds;
    private final Duration lockTtl;

    public ModelingKernel(ModelRepository models, ViewRepository views, ModelLockRepository locks,
                          ModelVersionRepository versions, ModelAccessListRepository accessLists,
                          IdempotencyRepository idempotency, UnitOfWork unitOfWork, DomainEventPublisher events,
                          UseCaseMetrics metrics, TextCatalog texts, Clock clock, Duration lockTtl) {
        this.models = Objects.requireNonNull(models);
        this.views = Objects.requireNonNull(views);
        this.locks = Objects.requireNonNull(locks);
        this.versions = Objects.requireNonNull(versions);
        this.accessLists = Objects.requireNonNull(accessLists);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.events = Objects.requireNonNull(events);
        this.metrics = Objects.requireNonNull(metrics);
        this.texts = Objects.requireNonNull(texts);
        this.clock = Objects.requireNonNull(clock);
        this.lockTtl = Objects.requireNonNull(lockTtl);
        this.uuids = new UuidV7(clock);
        this.archiIds = new ArchiIdGenerator(uuids::next);
    }

    /** Текущий момент по часам приложения. */
    Instant now() {
        return clock.instant();
    }

    /** Транзакции сценария. */
    UnitOfWork unitOfWork() {
        return unitOfWork;
    }

    /** Хранилище агрегатов модели. */
    ModelRepository models() {
        return models;
    }

    /** Хранилище представлений. */
    ViewRepository views() {
        return views;
    }

    /** История версий. */
    ModelVersionRepository versions() {
        return versions;
    }

    /** Тексты на языке запроса — для того, что сервер сохраняет как данные. */
    TextCatalog texts() {
        return texts;
    }

    /** Генератор ключей версии 7 по часам приложения. */
    UuidV7 uuids() {
        return uuids;
    }

    /** Генератор идентификаторов Archi. */
    ArchiIdGenerator archiIds() {
        return archiIds;
    }

    /** Хранилище блокировок. */
    ModelLockRepository locks() {
        return locks;
    }

    /** Хранилище списков доступа. */
    ModelAccessListRepository accessLists() {
        return accessLists;
    }

    /** Публикация доменных событий после коммита. */
    DomainEventPublisher events() {
        return events;
    }

    /** Метрики сценариев. */
    UseCaseMetrics metrics() {
        return metrics;
    }

    /** Срок блокировки модели. */
    Duration lockTtl() {
        return lockTtl;
    }

    /** Операция под метрикой use case'а и с проверкой роли. */
    <T> T run(Operation operation, EditorIdentity actor, Supplier<T> work) {
        return metrics.observe(operation.useCase(), () -> {
            AccessPolicy.require(actor, operation);
            return work.get();
        });
    }

    /** Заголовок модели, видимой автору с данным уровнем доступа. */
    ModelHeader visibleHeader(ModelId modelId, EditorIdentity actor, AclAccess access) {
        ModelHeader header = models.findHeader(modelId)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.MODEL, modelId)));
        accessLists.find(modelId).require(actor, access);
        return header;
    }

    /** Модель целиком, видимая автору с данным уровнем доступа. */
    ArchitectureModel visibleModel(ModelId modelId, EditorIdentity actor, AclAccess access) {
        visibleHeader(modelId, actor, access);
        return models.load(modelId)
                .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.MODEL, modelId)));
    }

    /**
     * Модель для записи содержимого: видима на запись, активна,
     * заблокирована автором команды.
     */
    ArchitectureModel writableModel(ModelId modelId, EditorIdentity actor) {
        ArchitectureModel model = visibleModel(modelId, actor, AclAccess.WRITE);
        model.requireActive();
        ModelLock.requireWriteAccess(modelId, locks.find(modelId).orElse(null), actor.subject(), now());
        return model;
    }

    /** Запись модели и публикация её событий. */
    void save(ArchitectureModel model) {
        models.save(model);
        events.publish(model.pullEvents());
    }

    /** Ключ нового объекта: заданный клиентом — если свободен, иначе следующий UUIDv7. */
    UUID newId(RequestedIds requested) {
        UUID id = requested.id();
        if (id == null) {
            return uuids.next();
        }
        if (models.idTaken(id)) {
            throw new ModelingDomainException(ModelingCodes.ID_TAKEN, Failure.CONFLICT,
                    Message.of(ModelingMessages.ID_TAKEN, id));
        }
        return id;
    }

    /**
     * {@code archiId} нового объекта модели. Заданный клиентом проверяется по формату
     * и по всему, что попадёт в тот же файл: концептам, папкам, представлениям, их узлам и рёбрам.
     */
    ArchiId newArchiId(ArchitectureModel model, RequestedIds requested) {
        return requested.archiId() != null ? requireFree(model, null, requested.archiId()) : newArchiId(model);
    }

    /** {@code archiId} нового узла или ребра: свободен и в модели, и на представлении. */
    ArchiId newArchiId(ArchitectureModel model, View view, RequestedIds requested) {
        return requested.archiId() != null
                ? requireFree(model, view, requested.archiId())
                : archiIds.nextUnique(id -> model.archiIdTaken(id) || view.archiIdTaken(id));
    }

    private ArchiId newArchiId(ArchitectureModel model) {
        return archiIds.nextUnique(model::archiIdTaken);
    }

    /** Заданный клиентом {@code archiId}, свободный в модели и — если {@code view} задано — на представлении. */
    private ArchiId requireFree(ArchitectureModel model, View view, String value) {
        ArchiId candidate;
        try {
            candidate = ArchiId.of(value);
        } catch (InvalidValueException invalid) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.ARCHI_ID_REQUESTED_INVALID, value));
        }
        if (model.archiIdTaken(candidate) || view != null && view.archiIdTaken(candidate)
                || models.diagramArchiIdTaken(model.id(), candidate)) {
            throw new ModelingDomainException(ModelingCodes.ID_TAKEN, Failure.CONFLICT,
                    Message.of(ModelingMessages.ARCHI_ID_TAKEN, candidate, model.id()));
        }
        return candidate;
    }

    /**
     * Команда с необязательным ключом идемпотентности. Ключ есть — повтор
     * с тем же телом отдаёт первый результат, перечитанный заново; другое тело — {@code 409}.
     *
     * @param key     ключ идемпотентности; {@code null} — не задан
     * @param execute выполнить и вернуть ссылку на результат
     * @param reload  результат по ссылке
     */
    <R> R idempotent(String scope, EditorIdentity actor, String key, String fingerprint,
                     Supplier<String> execute, Function<String, R> reload) {
        if (key == null) {
            return reload.apply(execute.get());
        }
        IdempotentCommand.requireValidKey(key);
        IdempotentCommand.Decision decision =
                IdempotentCommand.decide(idempotency.find(scope, actor.subject(), key).orElse(null), fingerprint);
        if (decision instanceof IdempotentCommand.Replay replay) {
            return reload.apply(replay.resultRef());
        }
        String resultRef = execute.get();
        idempotency.save(IdempotencyRecord.record(scope, actor.subject(), key, fingerprint, resultRef, now()));
        return reload.apply(resultRef);
    }
}
