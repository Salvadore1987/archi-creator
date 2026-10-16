package uz.salvadore.hamkorbank.archi.advisor.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetStatus;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Role;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;

class AdvisorAdminServiceTest {

    private static final Requester ADMIN = Requester.of("root", Role.ADMIN);

    @Test
    @DisplayName("INV-ADV-006: повышение лимита возвращает бюджет из EXHAUSTED, расход прежний")
    void raisingLimitReopensAdvisor() {
        Fixture fixture = new Fixture();
        fixture.limit = Optional.of(100L);
        fixture.service().ask(Fixture.ALICE, AdvisorRequest.review(Fixture.MODEL, Optional.empty()), "k",
                Locale.ENGLISH).run(new Fixture.Recorded());
        assertEquals(BudgetStatus.EXHAUSTED, fixture.admin().status(Fixture.ALICE, Optional.empty()).budgetStatus());

        fixture.admin().change(ADMIN, Optional.empty(), true, Optional.of(10_000L));

        AdvisorAdminService.Status status = fixture.admin().status(Fixture.ALICE, Optional.empty());
        assertEquals(BudgetStatus.OK, status.budgetStatus());
        assertEquals(110, fixture.budgets.values().iterator().next().consumedTokens());
    }

    @Test
    @DisplayName("FR-27: выключатель и лимит меняет только администратор; лимит положительный")
    void onlyAdminChangesSettings() {
        Fixture fixture = new Fixture();

        assertThrows(AdvisorDomainException.class,
                () -> fixture.admin().change(Fixture.ALICE, Optional.empty(), false, Optional.of(10L)));
        assertThrows(AdvisorDomainException.class,
                () -> fixture.admin().change(ADMIN, Optional.empty(), false, Optional.of(0L)));

        fixture.admin().change(ADMIN, Optional.empty(), false, Optional.of(10L));
        assertFalse(fixture.admin().status(Fixture.VIEWER, Optional.empty()).enabled());
        assertEquals(10L, fixture.admin().settings(ADMIN, Optional.empty()).monthlyTokenLimit());
    }

    @Test
    @DisplayName("Лимит не задан — действует значение по умолчанию, а не «без ограничения»")
    void missingLimitFallsBackToDefault() {
        Fixture fixture = new Fixture();
        fixture.limit = Optional.empty();

        assertEquals(5_000_000L, fixture.admin().settings(ADMIN, Optional.empty()).monthlyTokenLimit());
    }
}
