package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorFunction;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.SessionStatus;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

/**
 * Метрики помощника. Только размеры, тайминги и исходы: содержимое контекста
 * и ответа в метки не попадает никогда.
 */
public interface AdvisorMetrics {

    /** Выполнить сценарий, замерив его длительность и исход под именем use case'а. */
    <T> T observe(String useCase, Supplier<T> work);

    /** Исход сессии: завершена, неуспешна или отклонена и по какой причине. */
    void session(AdvisorFunction function, SessionStatus outcome, Optional<RejectionReason> reason);

    /** Время от обращения до первого токена ответа. */
    void firstToken(AdvisorFunction function, Duration sinceRequest);

    /** Израсходованные сессией токены — по функции и пространству. */
    void tokens(AdvisorFunction function, String workspace, TokenUsage usage);

    /** Заполнение бюджета пространства после списания или смены лимита. */
    void budget(TokenBudget budget);

    /** Контекст модели пришлось усечь, чтобы он поместился в запрос. */
    void contextTruncated(AdvisorFunction function);
}
