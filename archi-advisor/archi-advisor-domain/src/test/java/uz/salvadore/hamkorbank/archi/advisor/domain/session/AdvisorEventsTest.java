package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudgetExhaustedEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudgetThresholdReachedEvent;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

class AdvisorEventsTest {

    private static final BudgetPeriod OCTOBER = BudgetPeriod.parse("2026-10");

    @Test
    @DisplayName("событие сессии не строится без обязательных полей; причина отказа необязательна")
    void sessionEventRequiresFields() {
        assertDoesNotThrow(() -> completed(AdvisorSessionId.next(Sessions.UUIDS), Sessions.WORKSPACE, "architect"));
        assertThrows(NullPointerException.class, () -> completed(null, Sessions.WORKSPACE, "architect"));
        assertThrows(NullPointerException.class,
                () -> completed(AdvisorSessionId.next(Sessions.UUIDS), null, "architect"));
        assertThrows(NullPointerException.class,
                () -> completed(AdvisorSessionId.next(Sessions.UUIDS), Sessions.WORKSPACE, null));
    }

    @Test
    @DisplayName("события бюджета не строятся без пространства, периода и момента")
    void budgetEventsRequireFields() {
        assertThrows(NullPointerException.class,
                () -> new TokenBudgetExhaustedEvent(null, OCTOBER, 100, 100, Sessions.NOW));
        assertThrows(NullPointerException.class,
                () -> new TokenBudgetExhaustedEvent(Sessions.WORKSPACE, OCTOBER, 100, 100, null));
        assertThrows(NullPointerException.class,
                () -> new TokenBudgetThresholdReachedEvent(Sessions.WORKSPACE, null, 100, 80, 80, Sessions.NOW));
    }

    private static AdvisorSessionCompletedEvent completed(AdvisorSessionId id, WorkspaceId workspace, String actor) {
        return new AdvisorSessionCompletedEvent(id, workspace, Sessions.MODEL, AdvisorFunction.REVIEW,
                SessionStatus.COMPLETED, null, 1, 1, false, 0, 10, actor, Sessions.NOW);
    }
}
