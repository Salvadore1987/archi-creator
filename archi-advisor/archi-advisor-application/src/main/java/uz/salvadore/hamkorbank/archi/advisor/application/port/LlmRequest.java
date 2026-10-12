package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.List;
import java.util.Objects;

/**
 * Запрос к языковой модели: системный промпт, сообщение пользователя с контекстом
 * модели и инструменты, через которые приходят структурированные результаты.
 * Содержимое не логируется нигде — это модель заказчика.
 */
public record LlmRequest(String system, String user, List<ToolSpec> tools, int maxTokens) {

    public LlmRequest {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(user, "user");
        tools = List.copyOf(tools);
    }

    /** Строка запроса без содержимого — длины, а не текст. */
    @Override
    public String toString() {
        return "LlmRequest[system=" + system.length() + " chars, user=" + user.length() + " chars, tools="
                + tools.stream().map(ToolSpec::name).toList() + ", maxTokens=" + maxTokens + "]";
    }
}
