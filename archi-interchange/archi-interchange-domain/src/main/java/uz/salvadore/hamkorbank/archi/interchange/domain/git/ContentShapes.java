package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.util.List;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;

/**
 * Содержимое узла без позиций в файле: теги, атрибуты, текст и вложенность. Так сравниваются
 * диаграмма представления и значения объекта — описание перед узлами диаграммы сдвигает
 * их позиции, но диаграмму не меняет.
 */
final class ContentShapes {

    private ContentShapes() {
    }

    /** Форма одного элемента содержимого. */
    record Shape(String tag, List<Attribute> attributes, @Nullable String text, List<Shape> content) {
    }

    /** Формы содержимого в его порядке. */
    static List<Shape> of(List<? extends DocumentContent> content) {
        return content.stream().map(ContentShapes::shape).toList();
    }

    private static Shape shape(DocumentContent item) {
        return switch (item) {
            case DocumentNode node -> new Shape(node.tag(), node.attributes().list(), null, of(node.content()));
            case DocumentValue value -> new Shape(value.tag(), value.attributes().list(), value.text(), List.of());
            case RawXmlFragment raw -> new Shape("", List.of(), raw.xml(), List.of());
        };
    }
}
