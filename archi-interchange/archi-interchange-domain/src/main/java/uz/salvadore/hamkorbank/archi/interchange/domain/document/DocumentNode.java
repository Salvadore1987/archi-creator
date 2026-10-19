package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/**
 * Узел документа с собственным идентификатором: папка, элемент, связь,
 * представление, узел и ребро представления.
 *
 * <p>У узла непустой {@code archiId}, тег и позиция среди соседей,
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

    /** Вид узла документа: что за объект модели за ним стоит. */
    public enum Kind {
        /** Папка — {@code <folder>}. */
        FOLDER,
        /** Элемент модели — {@code <element>} с типом, кроме связи и представления. */
        ELEMENT,
        /** Связь — {@code <element>} с типом {@code …Relationship}. */
        RELATIONSHIP,
        /** Представление — {@code <element>} с типом {@code …Model}: диаграмма, скетч, холст. */
        VIEW,
        /** Узел представления — {@code <child>}. */
        VIEW_NODE,
        /** Ребро представления — {@code <sourceConnection>}. */
        VIEW_EDGE
    }

    public DocumentNode {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attributes, "attributes");
        content = List.copyOf(content);
        if (!TAGS.contains(tag)) {
            throw new InvalidValueException(InterchangeMessages.NOT_DOCUMENT_NODE, tag);
        }
        ArchiId id = ArchiId.of(attributes.get("id")
                .orElseThrow(() -> new InvalidValueException(InterchangeMessages.NODE_ID_MISSING, tag)));
        ContentOrder.requireDense(content);
        for (DocumentContent item : content) {
            if (item instanceof RawXmlFragment raw && !id.equals(raw.parentArchiId())) {
                throw new InvalidValueException(InterchangeMessages.FRAGMENT_MISADDRESSED, id, raw.parentArchiId());
            }
        }
    }

    /** Идентификатор узла из атрибута {@code id}. */
    public ArchiId archiId() {
        return ArchiId.of(attributes.get("id").orElseThrow());
    }

    /** Вид узла по тегу и типу. */
    public Kind kind() {
        return kind(tag, attributes);
    }

    /**
     * Вид узла по тегу и атрибутам — для читателя, пока узел ещё не собран. Тег {@code element}
     * делится по окончанию {@code xsi:type}: так Archi отличает связи и представления от элементов.
     */
    public static Kind kind(String tag, Attributes attributes) {
        return switch (tag) {
            case "folder" -> Kind.FOLDER;
            case "child" -> Kind.VIEW_NODE;
            case "sourceConnection" -> Kind.VIEW_EDGE;
            default -> {
                String type = attributes.get("xsi:type").orElse("");
                yield type.endsWith("Relationship") ? Kind.RELATIONSHIP
                        : type.endsWith("Model") ? Kind.VIEW : Kind.ELEMENT;
            }
        };
    }

    /** Значение {@code xsi:type}; у папок пусто. */
    public Optional<String> archiType() {
        return attributes.get("xsi:type");
    }

    /** Значение атрибута узла по имени. */
    public Optional<String> attribute(String name) {
        return attributes.get(name);
    }

    /** Вложенные узлы в порядке файла. */
    public List<DocumentNode> children() {
        return ContentOrder.only(content, DocumentNode.class);
    }

    /** Значения с данным тегом в порядке файла. */
    public List<DocumentValue> values(String tag) {
        return ContentOrder.only(content, DocumentValue.class).stream().filter(v -> v.tag().equals(tag)).toList();
    }

    /** Фрагменты XML, которые читатель не разобрал, в порядке файла. */
    public List<RawXmlFragment> rawFragments() {
        return ContentOrder.only(content, RawXmlFragment.class);
    }

    /** Текст первого {@code <documentation>} узла. */
    public Optional<String> documentation() {
        return values("documentation").stream().findFirst().map(DocumentValue::text);
    }
}
