package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки помощника ({@code archi.ai.*}).
 *
 * @param apiKey                     ключ Anthropic API; пустой — помощник отвечает «недоступен»,
 *                                   приложение стартует без него. В строковое представление не попадает
 * @param model                      модель Anthropic
 * @param baseUrl                    адрес API; тесты подменяют его локальным сервером
 * @param effort                     глубина рассуждения модели: меньше — быстрее первые токены
 * @param timeout                    предел одного обращения целиком
 * @param connectTimeout             предел установки соединения: недоступный адрес — отказ, а не минута ожидания
 * @param maxRetries                 повторы до первого токена; после него повторов нет
 * @param maxOutputTokens            предел ответа одного обращения
 * @param maxContextTokens           верхняя граница контекста одного запроса
 * @param minCoverage                доля элементов области, ниже которой усечённый контекст не отправляется
 * @param defaultMonthlyTokenLimit   лимит пространства, которому администратор его не задал
 * @param userRequestsPerMinute      частота обращений автора
 * @param workspaceRequestsPerMinute частота обращений пространства
 * @param budgetZone                 часовой пояс, по которому месяц бюджета начинается с первого числа
 * @param circuitBreaker             предохранитель: пока API отказывает, обращения отклоняются сразу
 */
@ConfigurationProperties("archi.ai")
@Validated
public record AiSettings(String apiKey,
                         @DefaultValue("claude-sonnet-5") @NotBlank String model,
                         @DefaultValue("https://api.anthropic.com") @Pattern(regexp = "^https?://.+") String baseUrl,
                         @DefaultValue("medium") @NotBlank String effort,
                         @DefaultValue("180s") @NotNull Duration timeout,
                         @DefaultValue("10s") @NotNull Duration connectTimeout,
                         @DefaultValue("2") @PositiveOrZero int maxRetries,
                         @DefaultValue("16000") @Positive int maxOutputTokens,
                         @DefaultValue("120000") @Positive int maxContextTokens,
                         @DefaultValue("0.25") @DecimalMin("0") @DecimalMax("1") double minCoverage,
                         @DefaultValue("5000000") @PositiveOrZero long defaultMonthlyTokenLimit,
                         @DefaultValue("5") @Positive int userRequestsPerMinute,
                         @DefaultValue("20") @Positive int workspaceRequestsPerMinute,
                         @DefaultValue("Asia/Tashkent") @NotNull ZoneId budgetZone,
                         @DefaultValue @Valid @NotNull Breaker circuitBreaker) {

    /**
     * Предохранитель на Anthropic API ({@code archi.ai.circuit-breaker.*}). Окно — последние обращения
     * по счёту; оценка начинается, когда окно заполнено.
     *
     * @param failureRateThreshold доля отказов и медленных обращений в окне, в процентах, при которой
     *                             предохранитель открывается
     * @param slowCallThreshold    обращение медленнее этого — как отказ: считается время до открытия потока
     * @param slidingWindowSize    сколько последних обращений в окне
     * @param waitInOpenState      сколько предохранитель открыт до пробных обращений
     */
    public record Breaker(@DefaultValue("50") @Positive @Max(100) int failureRateThreshold,
                          @DefaultValue("60s") @NotNull Duration slowCallThreshold,
                          @DefaultValue("10") @Positive int slidingWindowSize,
                          @DefaultValue("60s") @NotNull Duration waitInOpenState) {
    }

    /** Задан ли ключ API: без него помощник отвечает «недоступен», остальное приложение работает. */
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Без ключа: настройки попадают в журнал и в отчёты об ошибках привязки. */
    @Override
    public String toString() {
        return "AiSettings[apiKey=" + (configured() ? "***" : "") + ", model=" + model + ", baseUrl=" + baseUrl
                + ", effort=" + effort + ", timeout=" + timeout + ", connectTimeout=" + connectTimeout
                + ", maxRetries=" + maxRetries + ", maxOutputTokens=" + maxOutputTokens + ", maxContextTokens="
                + maxContextTokens + ", minCoverage=" + minCoverage
                + ", defaultMonthlyTokenLimit=" + defaultMonthlyTokenLimit + ", userRequestsPerMinute="
                + userRequestsPerMinute + ", workspaceRequestsPerMinute=" + workspaceRequestsPerMinute
                + ", budgetZone=" + budgetZone + ", circuitBreaker=" + circuitBreaker + "]";
    }
}
