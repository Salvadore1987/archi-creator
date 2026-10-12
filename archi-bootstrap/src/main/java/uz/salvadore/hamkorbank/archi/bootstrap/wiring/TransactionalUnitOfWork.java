package uz.salvadore.hamkorbank.archi.bootstrap.wiring;

import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;

/**
 * {@code UnitOfWork} обоих контекстов на {@link TransactionTemplate}. Распространение —
 * {@code REQUIRED}: импорт interchange и сохранение версии modeling внутри него — одна
 * транзакция.
 */
public final class TransactionalUnitOfWork implements UnitOfWork {

    private final TransactionTemplate write;
    private final TransactionTemplate read;

    public TransactionalUnitOfWork(PlatformTransactionManager transactions) {
        this.write = new TransactionTemplate(transactions);
        this.read = new TransactionTemplate(transactions);
        this.read.setReadOnly(true);
    }

    @Override
    public <T> T write(Supplier<T> work) {
        return write.execute(status -> work.get());
    }

    @Override
    public <T> T read(Supplier<T> work) {
        return read.execute(status -> work.get());
    }
}
