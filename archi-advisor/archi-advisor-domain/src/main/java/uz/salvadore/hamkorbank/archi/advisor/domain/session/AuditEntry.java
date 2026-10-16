package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import java.time.Instant;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AuditEntryId;

/**
 * Запись аудита обращения: кто, когда, какую функцию вызвал, сколько это стоило
 * и чем кончилось. Ровно одна на сессию, включая отклонённые и неуспешные.
 *
 * @param rejectionReason причина отказа; {@code null} — сессия не отклонена
 */
public record AuditEntry(AuditEntryId id, String actor, AdvisorFunction function, TokenUsage usage,
                         SessionStatus outcome, RejectionReason rejectionReason, Instant createdAt) {

    public AuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!outcome.terminal()) {
            throw new InvalidValueException(AdvisorMessages.AUDIT_OUTCOME_NOT_TERMINAL);
        }
    }
}
