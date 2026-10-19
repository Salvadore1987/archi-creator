package uz.salvadore.hamkorbank.archi.advisor.domain.budget;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;

/** Календарный месяц {@code YYYY-MM} — единица учёта и сброса лимита. */
public record BudgetPeriod(String value) {

    private static final Pattern FORMAT = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    public BudgetPeriod {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException(AdvisorMessages.PERIOD_INVALID, value);
        }
    }

    /** Период календарного месяца. */
    public static BudgetPeriod of(YearMonth month) {
        return new BudgetPeriod(month.toString());
    }

    /** Период из строки {@code YYYY-MM}; иной формат — {@link InvalidValueException}. */
    public static BudgetPeriod parse(String value) {
        if (value == null) {
            throw new InvalidValueException(AdvisorMessages.PERIOD_INVALID, value);
        }
        try {
            return of(YearMonth.parse(value));
        } catch (DateTimeParseException e) {
            throw new InvalidValueException(AdvisorMessages.PERIOD_INVALID, value);
        }
    }

    /** Месяц, в который попадает момент, — по часовому поясу учёта. */
    public static BudgetPeriod containing(Instant instant, ZoneId zone) {
        return of(YearMonth.from(instant.atZone(zone)));
    }

    /** Календарный месяц периода. */
    public YearMonth month() {
        return YearMonth.parse(value);
    }

    /** Первый день следующего месяца: с него расход начинается с нуля. */
    public LocalDate resetsAt() {
        return month().plusMonths(1).atDay(1);
    }

    @Override
    public String toString() {
        return value;
    }
}
