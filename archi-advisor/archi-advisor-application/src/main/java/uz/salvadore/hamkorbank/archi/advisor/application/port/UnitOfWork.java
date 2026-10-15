package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.function.Supplier;

/** Транзакция сценария. Своя у помощника: application-слой контекста не знает про modeling. */
public interface UnitOfWork {

    /** Выполнить работу в пишущей транзакции. */
    <T> T write(Supplier<T> work);

    /** Выполнить работу в транзакции только на чтение. */
    <T> T read(Supplier<T> work);
}
