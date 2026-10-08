package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UseCaseMetrics;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ViewRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiIdGenerator;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.UuidV7;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotencyRecord;
import uz.salvadore.hamkorbank.archi.modeling.domain.idempotency.IdempotentCommand;
import uz.salvadore.hamkorbank.archi.modeling.domain.lock.ModelLock;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Общее у всех сценариев modeling: зависимости и порядок проверок записи.
 *
 * <p>Порядок не случаен: роль (дёшево, без базы) → модель и список доступа (скрытая модель
 * отвечает {@code 404} раньше, чем выдаст себя чем-нибудь ещё) → состояние модели →
 * блокировка автора. Так клиент получает самый осмысленный отказ из возможных.
 */
public final class ModelingKernel {

    final ModelRepository models;
    final ViewRepository views;
    final ModelLockRepository locks;
    final ModelVersionRepository versions;
    final ModelAccessListRepository accessLists;
    final IdempotencyRepository idempotency;
    final UnitOfWork unitOfWork;
    final DomainEventPublisher events;
    final UseCaseMetrics metrics;
    final Clock clock;
    final UuidV7 uuids;
    final ArchiIdGenerator archiIds;
    final Duration lockTtl;

    public ModelingKernel(ModelRepository models, ViewRepository views, ModelLockRepository locks,
                          ModelVersionRepository versions, ModelAccessListRepository accessLists,
                          IdempotencyRepository idempotency, UnitOfWork unitOfWork, DomainEventPublisher events,
                          UseCaseMetrics metrics, Clock clock, Duration lockTtl) {
        this.models = Objects.requireNonNull(models);
        this.views = Objects.requireNonNull(views);
        this.locks = Objects.requireNonNull(locks);
        this.versions = Objects.requireNonNull(versions);
        this.accessLists = Objects.requireNonNull(accessLists);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.events = Objects.requireNonNull(events);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.lockTtl = Objects.requireNonNull(lockTtl);
        this.uuids = new UuidV7(clock);
        this.archiIds = new ArchiIdGenerator();
    }

    Instant now() {
        return clock.instant();
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
                .orElseThrow(() -> ModelingException.notFound("модель " + modelId));
        accessLists.find(modelId).require(actor, access);
        return header;
    }

    ArchitectureModel visibleModel(ModelId modelId, EditorIdentity actor, AclAccess access) {
        visibleHeader(modelId, actor, access);
        return models.load(modelId).orElseThrow(() -> ModelingException.notFound("модель " + modelId));
    }

    /**
     * Модель для записи содержимого: видима на запись, активна,
     * заблокирована автором команды.
     */
    ArchitectureModel writableModel(ModelId modelId, EditorIdentity actor) {
        ArchitectureModel model = visibleModel(modelId, actor, AclAccess.WRITE);
        model.requireActive();
        ModelLock.requireWriteAccess(modelId, locks.find(modelId), actor.subject(), now());
        return model;
    }

    void save(ArchitectureModel model) {
        models.save(model);
        events.publish(model.pullEvents());
    }

    ArchiId newArchiId(ArchitectureModel model) {
        return archiIds.nextUnique(model::archiIdTaken);
    }

    /**
     * Команда с необязательным ключом идемпотентности. Ключ есть — повтор
     * с тем же телом отдаёт первый результат, перечитанный заново; другое тело — {@code 409}.
     *
     * @param execute выполнить и вернуть ссылку на результат
     * @param reload  результат по ссылке
     */
    <R> R idempotent(String scope, EditorIdentity actor, Optional<String> key, String fingerprint,
                     Supplier<String> execute, Function<String, R> reload) {
        if (key.isEmpty()) {
            return reload.apply(execute.get());
        }
        IdempotentCommand.requireValidKey(key.get());
        IdempotentCommand.Decision decision =
                IdempotentCommand.decide(idempotency.find(scope, actor.subject(), key.get()), fingerprint);
        if (decision instanceof IdempotentCommand.Replay replay) {
            return reload.apply(replay.resultRef());
        }
        String resultRef = execute.get();
        idempotency.save(new IdempotencyRecord(scope, actor.subject(), key.get(), fingerprint, resultRef, now()));
        return reload.apply(resultRef);
    }
}
