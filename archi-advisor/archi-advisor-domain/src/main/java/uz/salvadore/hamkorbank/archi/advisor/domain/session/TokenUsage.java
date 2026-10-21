package uz.salvadore.hamkorbank.archi.advisor.domain.session;

import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.InvalidValueException;

/** Расход одного обращения: входные и выходные токены. Сумма помещается в {@code long}. */
public record TokenUsage(long promptTokens, long completionTokens) {

    /** Нулевой расход — у сессии, не дошедшей до внешнего API. */
    public static final TokenUsage NONE = new TokenUsage(0, 0);

    /** Символов текста на токен — оценка там, где точного счёта нет: размер контекста, оборванный ответ. */
    public static final int CHARS_PER_TOKEN = 4;

    public TokenUsage {
        if (promptTokens < 0 || completionTokens < 0) {
            throw new InvalidValueException(AdvisorMessages.USAGE_NEGATIVE);
        }
        sum(promptTokens, completionTokens);
    }

    /**
     * Сумма расходов; переполнение — отказ значения, а не отрицательное число: отрицательный
     * расход уменьшил бы израсходованное бюджетом.
     */
    public static long sum(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            InvalidValueException tooLarge = new InvalidValueException(AdvisorMessages.USAGE_TOO_LARGE);
            tooLarge.initCause(e);
            throw tooLarge;
        }
    }

    /** Оценка токенов текста из {@code chars} символов — с округлением вверх: недооценка дешевле расходу. */
    public static long estimate(long chars) {
        return chars <= 0 ? 0 : (chars - 1) / CHARS_PER_TOKEN + 1;
    }

    /** Входные и выходные токены вместе. */
    public long total() {
        return promptTokens + completionTokens;
    }

    /** Сумма двух расходов. */
    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(sum(promptTokens, other.promptTokens), sum(completionTokens, other.completionTokens));
    }
}
