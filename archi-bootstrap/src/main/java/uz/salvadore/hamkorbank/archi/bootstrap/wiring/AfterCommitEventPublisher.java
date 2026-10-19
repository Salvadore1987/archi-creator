package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import java.util.Collection;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Публикация доменных событий после коммита, в процессе, без брокера.
 * Откат транзакции событий не публикует. Окно потери — падение процесса между
 * коммитом и доставкой — принято: событие, которое обязано дойти (зафиксированная
 * версия для Git), пишется в outbox той же транзакцией, а не публикуется здесь.
 */
public final class AfterCommitEventPublisher {

    private final ApplicationEventPublisher publisher;

    public AfterCommitEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Публикует события после коммита текущей транзакции, вне транзакции — сразу.
     *
     * @param events события любого контекста; у каждого контекста свой тип события
     * @param <E> тип события контекста
     */
    public <E> void publish(Collection<E> events) {
        if (events.isEmpty()) {
            return;
        }
        List<E> copy = List.copyOf(events);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    copy.forEach(publisher::publishEvent);
                }
            });
        } else {
            copy.forEach(publisher::publishEvent);
        }
    }
}
