package uz.salvadore.hamkorbank.archi.bootstrap.web;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Контекст журнала запроса — {@code traceId}, модель, пользователь — переходит в задачу фонового исполнителя:
 * записи потока помощника несут тот же {@code traceId}, что и запрос, который его открыл. После задачи поток
 * исполнителя возвращается к своему контексту, чужой в нём не остаётся.
 */
public final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> own = MDC.getCopyOfContextMap();
            replace(captured);
            try {
                task.run();
            } finally {
                replace(own);
            }
        };
    }

    private static void replace(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
