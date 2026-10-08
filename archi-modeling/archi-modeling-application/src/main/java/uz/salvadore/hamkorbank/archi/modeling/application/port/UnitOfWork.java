package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.function.Supplier;

/**
 * Транзакция без фреймворка в application-слое: реализация — {@code TransactionTemplate}
 * в {@code archi-bootstrap}. Вложенный вызов присоединяется к внешней транзакции.
 */
public interface UnitOfWork {

    <T> T write(Supplier<T> work);

    <T> T read(Supplier<T> work);

    default void write(Runnable work) {
        write(() -> {
            work.run();
            return null;
        });
    }
}
