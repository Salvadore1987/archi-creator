package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import java.util.Collection;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Публикация доменных событий после коммита, в процессе, без брокера.
 * Откат транзакции событий не публикует. Окно потери — падение процесса между
 * коммитом и доставкой — принято до этапа 7a, где нужен outbox.
 */
public final class AfterCommitEventPublisher {

    private final ApplicationEventPublisher publisher;

    public AfterCommitEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(Collection<?> events) {
        if (events.isEmpty()) {
            return;
        }
        List<?> copy = List.copyOf(events);
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
