package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import java.time.Clock;
import java.util.Collection;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.port.DomainEventPublisher;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.modeling.domain.event.ModelVersionCommittedEvent;

/**
 * События modeling: зафиксированная версия пишется в outbox сразу — в транзакции
 * сохранения, — а всё вместе уходит в процесс после коммита, как раньше. Откат
 * сохранения не оставляет записи; коммит не теряет её даже при падении процесса.
 */
public final class OutboxEventPublisher implements DomainEventPublisher {

    private final AfterCommitEventPublisher afterCommit;
    private final GitOutbox outbox;
    private final Clock clock;

    public OutboxEventPublisher(AfterCommitEventPublisher afterCommit, GitOutbox outbox, Clock clock) {
        this.afterCommit = afterCommit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void publish(Collection<DomainEvent> events) {
        for (DomainEvent event : events) {
            if (event instanceof ModelVersionCommittedEvent committed) {
                outbox.enqueue(new WorkspaceId(committed.workspaceId().value()),
                        new ModelId(committed.modelId().value()), committed.versionNo(), clock.instant());
            }
        }
        afterCommit.publish(events);
    }
}
