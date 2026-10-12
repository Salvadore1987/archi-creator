package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.TokenUsage;

/** События потока языковой модели. */
public interface LlmListener {

    /** Фрагмент текста ответа. */
    void text(String delta);

    /** Завершённый вызов инструмента — разобранный JSON входа. */
    void toolCall(String name, Map<String, Object> input);

    /** Расход нарастающим итогом: приходит в начале и по ходу ответа. */
    void usage(TokenUsage cumulative);
}
