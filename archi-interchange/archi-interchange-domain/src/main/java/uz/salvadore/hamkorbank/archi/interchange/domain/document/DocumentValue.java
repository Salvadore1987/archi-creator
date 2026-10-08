package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Значение без собственного идентификатора: {@code documentation}, {@code purpose},
 * {@code property}, {@code bounds}, {@code bendpoint}, {@code content}.
 *
 * <p>Хранится обобщённо — тег, атрибуты, текст, — чтобы незнакомый атрибут
 * у знакомого тега не потерялся. Типизированное чтение ({@code x}, {@code y} у
 * {@code bounds}) — забота потребителя, а не формы хранения.
 *
 * @param text текст элемента; пусто — элемент без текста ({@code <bounds …/>})
 */
public record DocumentValue(String tag, DocumentOrder order, Attributes attributes, Optional<String> text)
        implements DocumentContent {

    /** Теги, которые читатель понимает как значения. Остальное без {@code id} — {@link RawXmlFragment}. */
    public static final Set<String> TAGS = Set.of("documentation", "purpose", "property", "bounds", "bendpoint", "content");

    public DocumentValue {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(text, "text");
        if (!TAGS.contains(tag)) {
            throw new IllegalArgumentException("не значение: " + tag);
        }
    }

    public Optional<String> attribute(String name) {
        return attributes.get(name);
    }
}
