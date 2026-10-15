package uz.salvadore.hamkorbank.archi.bootstrap.advisor;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Настройки помощника ({@code archi.ai.*}).
 *
 * @param apiKey                     ключ Anthropic API; пустой — помощник отвечает «недоступен»,
 *                                   приложение стартует без него. В строковое представление не попадает
 * @param model                      модель Anthropic
 * @param baseUrl                    адрес API; тесты подменяют его локальным сервером
 * @param effort                     глубина рассуждения модели: меньше — быстрее первые токены
 * @param timeout                    предел одного обращения целиком
 * @param maxRetries                 повторы до первого токена; после него повторов нет
 * @param maxOutputTokens            предел ответа одного обращения
 * @param maxContextTokens           верхняя граница контекста одного запроса
 * @param minCoverage                доля элементов области, ниже которой усечённый контекст не отправляется
 * @param defaultMonthlyTokenLimit   лимит пространства, которому администратор его не задал
 * @param userRequestsPerMinute      частота обращений автора
 * @param workspaceRequestsPerMinute частота обращений пространства
 * @param budgetZone                 часовой пояс, по которому месяц бюджета начинается с первого числа
 */
@ConfigurationProperties("archi.ai")
public record AiSettings(String apiKey,
                         @DefaultValue("claude-sonnet-5") String model,
                         @DefaultValue("https://api.anthropic.com") String baseUrl,
                         @DefaultValue("medium") String effort,
                         @DefaultValue("180s") Duration timeout,
                         @DefaultValue("2") int maxRetries,
                         @DefaultValue("16000") int maxOutputTokens,
                         @DefaultValue("120000") int maxContextTokens,
                         @DefaultValue("0.25") double minCoverage,
                         @DefaultValue("5000000") long defaultMonthlyTokenLimit,
                         @DefaultValue("5") int userRequestsPerMinute,
                         @DefaultValue("20") int workspaceRequestsPerMinute,
                         @DefaultValue("Asia/Tashkent") ZoneId budgetZone) {

    /** Задан ли ключ API: без него помощник отвечает «недоступен», остальное приложение работает. */
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Без ключа: настройки попадают в журнал и в отчёты об ошибках привязки. */
    @Override
    public String toString() {
        return "AiSettings[apiKey=" + (configured() ? "***" : "") + ", model=" + model + ", baseUrl=" + baseUrl
                + ", effort=" + effort + ", timeout=" + timeout + ", maxRetries=" + maxRetries + ", maxOutputTokens="
                + maxOutputTokens + ", maxContextTokens=" + maxContextTokens + ", minCoverage=" + minCoverage
                + ", defaultMonthlyTokenLimit=" + defaultMonthlyTokenLimit + ", userRequestsPerMinute="
                + userRequestsPerMinute + ", workspaceRequestsPerMinute=" + workspaceRequestsPerMinute
                + ", budgetZone=" + budgetZone + "]";
    }
}
