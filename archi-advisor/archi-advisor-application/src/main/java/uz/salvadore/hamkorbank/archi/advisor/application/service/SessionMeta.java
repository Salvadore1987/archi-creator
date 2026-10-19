package uz.salvadore.hamkorbank.archi.advisor.application.service;

import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.RejectionReason;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.SessionStatus;

/**
 * Итог сессии для последнего кадра потока: исход, расход, было ли усечение контекста.
 *
 * @param rejectionReason причина отказа; {@code null} — сессия не отклонена
 */
public record SessionMeta(AdvisorSessionId sessionId, SessionStatus outcome, RejectionReason rejectionReason,
                          long promptTokens, long completionTokens, boolean contextTruncated) {

    static SessionMeta of(AdvisorSession session) {
        return new SessionMeta(session.id(), session.status(), session.rejectionReason(),
                session.usage().promptTokens(), session.usage().completionTokens(), session.contextTruncated());
    }
}
