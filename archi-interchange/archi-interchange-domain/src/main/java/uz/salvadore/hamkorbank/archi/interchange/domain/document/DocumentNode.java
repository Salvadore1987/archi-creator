package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Узел документа с собственным идентификатором: папка, элемент, связь,
 * представление, узел и ребро представления.
 *
 * <p>INV-IXC-001: у узла непустой {@code archiId}, тег и позиция среди соседей,
 * а всё, что внутри, лежит в {@link #content()} в порядке файла — вложенные узлы,
 * значения и непрозрачные фрагменты вперемешку, как в исходнике. Атрибуты — все,
 * включая незнакомые, в исходном порядке.
 *
 * @param tag {@code folder}, {@code element}, {@code child} или {@code sourceConnection};
 *            тип объекта — в атрибуте {@code xsi:type}, у папок его нет
 */
public record DocumentNode(String tag, DocumentOrder order, Attributes attributes, List<DocumentContent> content)
        implements DocumentContent {

    /** Теги узлов с идентификатором, которые кодек разбирает сам. */
    public static final Set<String> TAGS = Set.of("folder", "element", "child", "sourceConnection");

    public DocumentNode {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attributes, "attributes");
        content = List.copyOf(content);
        if (!TAGS.contains(tag)) {
            throw new IllegalArgumentException("не узел документа: " + tag);
        }
        ArchiId id = ArchiId.of(attributes.get("id")
                .orElseThrow(() -> new IllegalArgumentException("INV-IXC-001: у узла <" + tag + "> нет id")));
        ContentOrder.requireDense(content);
        for (DocumentContent item : content) {
            if (item instanceof RawXmlFragment raw && !raw.parentArchiId().equals(Optional.of(id))) {
                throw new IllegalArgumentException("фрагмент внутри " + id + " адресован " + raw.parentArchiId());
            }
        }
    }

    public ArchiId archiId() {
        return ArchiId.of(attributes.get("id").orElseThrow());
    }

    /** Значение {@code xsi:type}; у папок пусто. */
    public Optional<String> archiType() {
        return attributes.get("xsi:type");
    }

    public Optional<String> attribute(String name) {
        return attributes.get(name);
    }

    /** Вложенные узлы в порядке файла. */
    public List<DocumentNode> children() {
        return ContentOrder.only(content, DocumentNode.class);
    }

    public List<DocumentValue> values(String tag) {
        return ContentOrder.only(content, DocumentValue.class).stream().filter(v -> v.tag().equals(tag)).toList();
    }

    public List<RawXmlFragment> rawFragments() {
        return ContentOrder.only(content, RawXmlFragment.class);
    }

    public Optional<String> documentation() {
        return values("documentation").stream().findFirst().flatMap(DocumentValue::text);
    }
}
