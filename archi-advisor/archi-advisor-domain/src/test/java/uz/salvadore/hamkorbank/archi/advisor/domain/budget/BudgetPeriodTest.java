package uz.salvadore.hamkorbank.archi.advisor.domain.budget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorCodes;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;

class BudgetPeriodTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2026-13", "2026-00", "2026-1", "26-10", "2026/10", "2026-10-01"})
    @DisplayName("Период не в виде YYYY-MM с месяцем от 01 до 12 не создаётся")
    void malformedPeriodIsRejected(String value) {
        InvalidValueException refused = assertThrows(InvalidValueException.class, () -> new BudgetPeriod(value));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.PERIOD_INVALID, value), refused.reason());
    }

    @Test
    @DisplayName("Разбор пустой ссылки вместо строки периода — отказ с причиной, а не NullPointerException")
    void parseOfNullIsRejected() {
        InvalidValueException refused = assertThrows(InvalidValueException.class, () -> BudgetPeriod.parse(null));

        assertEquals(AdvisorCodes.INVALID_INPUT, refused.code());
        assertEquals(Message.of(AdvisorMessages.PERIOD_INVALID, (Object) null), refused.reason());
    }
}
