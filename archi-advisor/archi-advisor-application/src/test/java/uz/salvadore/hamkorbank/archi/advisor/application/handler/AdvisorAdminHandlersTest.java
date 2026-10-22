package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ConfigureAdvisorCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorSettingsQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorStatusQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorStatusResponse;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Role;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;

class AdvisorAdminHandlersTest {

    private static final Requester ADMIN = Requester.of("root", Role.ADMIN);

    @Test
    @DisplayName("INV-ADV-006: расход сверх лимита переводит бюджет в EXHAUSTED")
    void spendingOverLimitExhaustsBudget() {
        Fixture fixture = exhausted();

        assertEquals("EXHAUSTED",
                fixture.admin().getAdvisorStatus(Fixture.ALICE, new GetAdvisorStatusQuery(null)).budgetStatus());
    }

    @Test
    @DisplayName("INV-ADV-006: повышение лимита возвращает бюджет из EXHAUSTED, расход прежний")
    void raisingLimitReopensAdvisor() {
        Fixture fixture = exhausted();

        fixture.admin().configureAdvisor(ADMIN, new ConfigureAdvisorCommand(null, true, 10_000L));

        AdvisorStatusResponse status = fixture.admin().getAdvisorStatus(Fixture.ALICE, new GetAdvisorStatusQuery(null));
        assertEquals("OK", status.budgetStatus());
        assertEquals(110, fixture.budgets.values().iterator().next().consumedTokens());
    }

    @Test
    @DisplayName("Датчик бюджета обновляется после коммита: откат не оставит в нём значения, которого нет в базе")
    void budgetGaugeFollowsCommit() {
        Fixture fixture = new Fixture();

        fixture.admin().configureAdvisor(ADMIN, new ConfigureAdvisorCommand(null, true, 10_000L));

        assertEquals(List.of(false), fixture.budgetGaugeInTransaction);
    }

    @Test
    @DisplayName("FR-27: выключатель и лимит не меняет архитектор")
    void architectDoesNotChangeSettings() {
        Fixture fixture = new Fixture();

        AdvisorDomainException denied = assertThrows(AdvisorDomainException.class,
                () -> fixture.admin().configureAdvisor(Fixture.ALICE, new ConfigureAdvisorCommand(null, false, 10L)));

        assertEquals(AdvisorCodes.ACCESS_DENIED, denied.code());
        assertEquals(Failure.FORBIDDEN, denied.failure());
    }

    @Test
    @DisplayName("FR-27: лимит — положительный")
    void zeroLimitIsRejected() {
        Fixture fixture = new Fixture();

        AdvisorDomainException rejected = assertThrows(AdvisorDomainException.class,
                () -> fixture.admin().configureAdvisor(ADMIN, new ConfigureAdvisorCommand(null, false, 0L)));

        assertEquals(AdvisorCodes.INVALID_INPUT, rejected.code());
        assertEquals(Failure.UNPROCESSABLE, rejected.failure());
        assertEquals(AdvisorMessages.LIMIT_NOT_POSITIVE, rejected.reason().key());
    }

    @Test
    @DisplayName("FR-27: администратор выключает помощника и меняет лимит")
    void adminChangesSettings() {
        Fixture fixture = new Fixture();

        fixture.admin().configureAdvisor(ADMIN, new ConfigureAdvisorCommand(null, false, 10L));

        assertFalse(fixture.admin().getAdvisorStatus(Fixture.VIEWER, new GetAdvisorStatusQuery(null)).enabled());
        assertEquals(10L,
                fixture.admin().getAdvisorSettings(ADMIN, new GetAdvisorSettingsQuery(null)).monthlyTokenLimit());
    }

    @Test
    @DisplayName("Лимит не задан — действует значение по умолчанию, а не «без ограничения»")
    void missingLimitFallsBackToDefault() {
        Fixture fixture = new Fixture();
        fixture.limit = null;

        assertEquals(5_000_000L,
                fixture.admin().getAdvisorSettings(ADMIN, new GetAdvisorSettingsQuery(null)).monthlyTokenLimit());
    }

    private static Fixture exhausted() {
        Fixture fixture = new Fixture();
        fixture.limit = 100L;
        fixture.service().ask(Fixture.ALICE, AdvisorRequest.review(Fixture.MODEL, null), "k",
                Locale.ENGLISH).run(new Fixture.Recorded());
        return fixture;
    }
}
