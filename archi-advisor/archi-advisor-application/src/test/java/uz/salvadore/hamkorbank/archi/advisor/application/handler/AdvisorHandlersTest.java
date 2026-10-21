package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ConfigureAdvisorCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorSettingsQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorStatusQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetTokenUsageQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorAdminApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;

class AdvisorHandlersTest {

    private static final Requester NOBODY = Requester.of("nobody");

    @Test
    @DisplayName("FR-28: роль проверяется до любой работы у всех сценариев помощника")
    void roleIsCheckedBeforeAnyWork() {
        Fixture fixture = new Fixture();
        fixture.duringRead = () -> {
            throw new AssertionError("модель читается до проверки роли");
        };
        Fixture.Advisor advisor = fixture.service();
        AdvisorAdminApplicationService admin = fixture.admin();
        Locale ru = Locale.forLanguageTag("ru");
        List<Executable> scenarios = List.of(
                () -> advisor.ask(NOBODY, AdvisorRequest.review(Fixture.MODEL, null), "k", ru),
                () -> advisor.ask(NOBODY, AdvisorRequest.explain(Fixture.MODEL, "crm"), "k", ru),
                () -> advisor.ask(NOBODY, AdvisorRequest.plan(Fixture.MODEL, "План"), "k", ru),
                () -> advisor.ask(NOBODY, AdvisorRequest.document(Fixture.MODEL, null, null), "k", ru),
                () -> admin.getAdvisorStatus(NOBODY, new GetAdvisorStatusQuery(null)),
                () -> admin.getAdvisorSettings(NOBODY, new GetAdvisorSettingsQuery(null)),
                () -> admin.configureAdvisor(NOBODY, new ConfigureAdvisorCommand(null, true, 10L)),
                () -> admin.getTokenUsage(NOBODY, new GetTokenUsageQuery(null, null)));

        for (Executable scenario : scenarios) {
            assertEquals(Failure.FORBIDDEN, assertThrows(AdvisorDomainException.class, scenario).failure());
        }
        assertEquals(0, fixture.transactions, "ни одной транзакции до проверки роли");
        assertTrue(fixture.upstreamCalls.isEmpty());
    }
}
