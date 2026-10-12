package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.function.Supplier;

/** Транзакция сценария. Своя у помощника: application-слой контекста не знает про modeling. */
public interface UnitOfWork {

    <T> T write(Supplier<T> work);

    <T> T read(Supplier<T> work);
}
