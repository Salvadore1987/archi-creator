package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.function.Supplier;

/**
 * Метрики use case'ов из spec/nfr/modeling.yaml#observability: счётчик запросов с исходом,
 * длительность и ошибки с {@code error_code} — кодом инварианта.
 */
public interface UseCaseMetrics {

    <T> T observe(String useCase, Supplier<T> work);

    UseCaseMetrics NONE = new UseCaseMetrics() {
        @Override
        public <T> T observe(String useCase, Supplier<T> work) {
            return work.get();
        }
    };
}
