package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.util.function.Supplier;

/** Метрики use case'ов interchange (spec/nfr/interchange.yaml#observability). */
public interface InterchangeMetrics {

    <T> T observe(String useCase, Supplier<T> work);

    InterchangeMetrics NONE = new InterchangeMetrics() {
        @Override
        public <T> T observe(String useCase, Supplier<T> work) {
            return work.get();
        }
    };
}
