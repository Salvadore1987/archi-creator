package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

/**
 * Полное содержимое модели в порядке исходного файла — published language
 * с BC modeling. Агрегатом не является: ни идентичности, ни жизненного цикла,
 * живёт внутри одного преобразования.
 *
 * <p>Корень хранится так же, как узел: атрибуты {@code archimate:model} в порядке
 * файла (объявления пространств имён — первыми) и общий список содержимого.
 * Срезы из спеки — {@link #folders()}, {@link #elements()}, {@link #relationships()},
 * {@link #views()}, {@link #orphanFragments()} — вычисляются из дерева, а не хранятся
 * рядом с ним: вторая копия разошлась бы с первой.
 */
public record ModelDocument(Attributes attributes, List<DocumentContent> content) {

    public static final String NAMESPACE = "http://www.archimatetool.com/archimate";

    public ModelDocument {
        Objects.requireNonNull(attributes, "attributes");
        content = List.copyOf(content);
        ArchiId.of(attributes.get("id")
                .orElseThrow(() -> new InvalidValueException(InterchangeMessages.MODEL_WITHOUT_ID)));
        ContentOrder.requireDense(content);
        for (DocumentContent item : content) {
            if (item instanceof RawXmlFragment raw && raw.parentArchiId().isPresent()) {
                throw new InvalidValueException(InterchangeMessages.FRAGMENT_ROOT_MISADDRESSED, raw.parentArchiId());
            }
        }
    }

    public ArchiId archiId() {
        return ArchiId.of(attributes.get("id").orElseThrow());
    }

    public String name() {
        return attributes.get("name").orElse("");
    }

    public String archiVersion() {
        return attributes.get("version").orElse("");
    }

    /** Описание модели — в файле Archi это {@code <purpose>}, а не {@code <documentation>}. */
    public Optional<String> documentation() {
        return ContentOrder.only(content, DocumentValue.class).stream()
                .filter(v -> v.tag().equals("purpose"))
                .findFirst()
                .flatMap(DocumentValue::text);
    }

    /** Папки верхнего уровня; вложенные — внутри них. */
    public List<DocumentNode> folders() {
        return ContentOrder.only(content, DocumentNode.class).stream().filter(n -> n.tag().equals("folder")).toList();
    }

    /** Все узлы документа обходом в глубину, в порядке файла. */
    public List<DocumentNode> allNodes() {
        List<DocumentNode> nodes = new ArrayList<>();
        ContentOrder.only(content, DocumentNode.class).forEach(n -> collect(n, nodes));
        return nodes;
    }

    /** Элементы модели: {@code <element>} кроме связей и представлений. */
    public List<DocumentNode> elements() {
        return modelObjects().stream().filter(n -> !isRelationship(n) && !isView(n)).toList();
    }

    /** Связи: {@code <element>} с типом {@code …Relationship}. */
    public List<DocumentNode> relationships() {
        return modelObjects().stream().filter(ModelDocument::isRelationship).toList();
    }

    /** Представления: {@code <element>} с типом {@code …Model} — диаграмма, скетч, холст. */
    public List<DocumentNode> views() {
        return modelObjects().stream().filter(ModelDocument::isView).toList();
    }

    /** Непрозрачные фрагменты прямо в корне модели. */
    public List<RawXmlFragment> orphanFragments() {
        return ContentOrder.only(content, RawXmlFragment.class);
    }

    public Optional<DocumentNode> find(ArchiId id) {
        return allNodes().stream().filter(n -> n.archiId().equals(id)).findFirst();
    }

    private List<DocumentNode> modelObjects() {
        return allNodes().stream().filter(n -> n.tag().equals("element")).toList();
    }

    private static boolean isRelationship(DocumentNode node) {
        return node.archiType().filter(t -> t.endsWith("Relationship")).isPresent();
    }

    private static boolean isView(DocumentNode node) {
        return node.archiType().filter(t -> t.endsWith("Model")).isPresent();
    }

    private static void collect(DocumentNode node, List<DocumentNode> into) {
        into.add(node);
        node.children().forEach(child -> collect(child, into));
    }
}
