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

    <T> T observe(String useCase, Supplier<T> work);

    void session(AdvisorFunction function, SessionStatus outcome, Optional<RejectionReason> reason);

    void firstToken(AdvisorFunction function, Duration sinceRequest);

    void tokens(AdvisorFunction function, String workspace, TokenUsage usage);

    void budget(TokenBudget budget);

    void contextTruncated(AdvisorFunction function);
}
