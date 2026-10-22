package uz.salvadore.hamkorbank.archi.advisor.domain.common;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Сообщение пользователю — ключ ресурса и аргументы, а не готовый текст. Текст
 * собирается на границе приложения по языку запроса; аргумент сам может быть
 * сообщением.
 *
 * <p>Своя копия, а не общая с modeling: доменные модули друг от друга не зависят.
 */
public record Message(String key, List<Object> args) {

    public Message {
        Objects.requireNonNull(key, "key");
        args = List.copyOf(args.stream().map(a -> a == null ? "" : a).toList());
    }

    /** Сообщение по ключу; {@code null} среди аргументов становится пустой строкой. */
    public static Message of(String key, @Nullable Object... args) {
        return new Message(key, Arrays.asList(args));
    }

    /** Ключ и аргументы — для журнала и отладки, не для пользователя. */
    @Override
    public String toString() {
        return args.isEmpty() ? key : key + args;
    }
}
