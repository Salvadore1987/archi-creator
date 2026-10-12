package uz.salvadore.hamkorbank.archi.advisor.domain.session;

/** Почему обращение отклонено до вызова внешнего API. */
public enum RejectionReason {
    ADVISOR_DISABLED,
    BUDGET_EXHAUSTED,
    RATE_LIMITED,
    CONTEXT_TOO_LARGE,
    MODEL_DELETED
}
