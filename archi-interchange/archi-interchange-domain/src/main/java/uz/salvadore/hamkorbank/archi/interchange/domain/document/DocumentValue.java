package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.XmlText;

/**
 * Значение без собственного идентификатора: {@code documentation}, {@code purpose},
 * {@code property}, {@code bounds}, {@code bendpoint}, {@code content}.
 *
 * <p>Хранится обобщённо — тег, атрибуты, текст, — чтобы незнакомый атрибут
 * у знакомого тега не потерялся. Типизированное чтение ({@code x}, {@code y} у
 * {@code bounds}) — забота потребителя, а не формы хранения.
 *
 * @param text текст элемента; {@code null} — элемент без текста ({@code <bounds …/>})
 */
public record DocumentValue(String tag, DocumentOrder order, Attributes attributes, @Nullable String text)
        implements DocumentContent {

    /** Теги, которые читатель понимает как значения. Остальное без {@code id} — {@link RawXmlFragment}. */
    public static final Set<String> TAGS = Set.of("documentation", "purpose", "property", "bounds", "bendpoint",
            "content");

    public DocumentValue {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attributes, "attributes");
        if (!TAGS.contains(tag)) {
            throw new InvalidValueException(InterchangeMessages.NOT_DOCUMENT_VALUE, tag);
        }
        XmlText.require(text, tag);
    }

    /** Значение атрибута по имени. */
    public Optional<String> attribute(String name) {
        return attributes.get(name);
    }
}
