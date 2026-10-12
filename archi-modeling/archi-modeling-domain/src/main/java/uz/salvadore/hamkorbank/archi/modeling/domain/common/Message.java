package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Сообщение пользователю — ключ ресурса и аргументы, а не готовый текст. Текст
 * собирается на границе приложения по языку запроса; аргумент сам может быть
 * сообщением (название объекта в падеже нужного языка).
 */
public record Message(String key, List<Object> args) {

    public Message {
        Objects.requireNonNull(key, "key");
        args = List.copyOf(args.stream().map(a -> a == null ? "" : a).toList());
    }

    public static Message of(String key, Object... args) {
        return new Message(key, Arrays.asList(args));
    }

    /** Ключ и аргументы — для журнала и отладки, не для пользователя. */
    @Override
    public String toString() {
        return args.isEmpty() ? key : key + args;
    }
}
