package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.function.Supplier;

/**
 * Транзакция без фреймворка в application-слое: реализация — {@code TransactionTemplate}
 * в {@code archi-bootstrap}. Вложенный вызов присоединяется к внешней транзакции.
 */
public interface UnitOfWork {

    /** Выполняет работу в транзакции только на чтение. */
    <T> T read(Supplier<T> work);

    /** Выполняет работу в транзакции на запись и возвращает её результат. */
    <T> T write(Supplier<T> work);

    /** Транзакция на запись для работы без результата. */
    default void write(Runnable work) {
        write(() -> {
            work.run();
            return null;
        });
    }
}
