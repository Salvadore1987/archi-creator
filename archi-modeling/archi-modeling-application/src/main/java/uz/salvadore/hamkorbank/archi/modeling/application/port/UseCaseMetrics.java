package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Метрики modeling: use case'ы — счётчик запросов
 * с исходом, длительность и ошибки с {@code error_code} (кодом инварианта); плюс время
 * ожидания блокировки, размер модели и находки валидации.
 */
public interface UseCaseMetrics {

    <T> T observe(String useCase, Supplier<T> work);

    /**
     * {@code modeling_lock_wait_seconds}: сколько пришлось бы ждать захватившему —
     * остаток срока чужой блокировки в момент отказа. Сервер не ждёт сам: второй
     * редактор получает {@code 409} сразу.
     */
    default void lockWait(ModelId modelId, Duration remaining) {
    }

    /** {@code modeling_model_elements}: размер модели при открытии (рассчитано до 2 000). */
    default void modelSize(ModelId modelId, int elements) {
    }

    /** {@code modeling_validation_findings_total}: находки последнего отчёта по уровню. */
    default void validationFindings(ModelId modelId, Map<String, Long> bySeverity) {
    }

    UseCaseMetrics NONE = new UseCaseMetrics() {
        @Override
        public <T> T observe(String useCase, Supplier<T> work) {
            return work.get();
        }
    };
}
