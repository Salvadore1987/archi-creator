package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

/**
 * Строка разбивки расхода.
 *
 * @param key      {@code subject} автора или имя функции
 * @param rejected из обращений отклонено до вызова внешнего API
 */
public record TokenUsageRowResponse(String key, long requests, long rejected, long promptTokens,
                                    long completionTokens) {
}
