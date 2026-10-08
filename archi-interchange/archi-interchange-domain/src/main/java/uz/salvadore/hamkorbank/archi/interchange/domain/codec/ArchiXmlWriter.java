package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attributes;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;

/**
 * Детерминированная запись {@code .archimate} в раскладке, которой
 * пишет сам Archi: UTF-8, отступ два пробела, LF, атрибуты в порядке документа.
 *
 * <p>Ничего не зависит ни от времени, ни от локали, ни от обхода хеш-таблиц:
 * порядок атрибутов и содержимого берётся из документа, а тот — из исходного
 * файла. Непрозрачный фрагмент выводится по своему адресу дословно;
 * собственный стиль объекта ({@code fillColor}, {@code font}, {@code lineColor})
 * — обычные атрибуты узла и выходят как прочитаны.
 *
 * <p>Обходит дерево в том же порядке, что читатель: содержимое узла — один
 * список, и писатель идёт по нему подряд.
 */
public final class ArchiXmlWriter implements ArchiDocumentWriter {

    private static final String DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String INDENT = "  ";

    @Override
    public void write(ModelDocument document, OutputStream out) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(out, "out");
        StringBuilder xml = new StringBuilder(64 * 1024);
        xml.append(DECLARATION);
        element(xml, 0, rootTag(document.attributes()), document.attributes(), document.content());
        try {
            out.write(xml.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void content(StringBuilder xml, int depth, List<DocumentContent> content) {
        for (DocumentContent item : content) {
            switch (item) {
                case DocumentNode node -> element(xml, depth, node.tag(), node.attributes(), node.content());
                case DocumentValue value -> value(xml, depth, value);
                case RawXmlFragment raw -> indent(xml, depth).append(raw.xml()).append('\n');
            }
        }
    }

    private static void element(StringBuilder xml, int depth, String tag, Attributes attributes,
                                List<DocumentContent> content) {
        startTag(indent(xml, depth), tag, attributes);
        if (content.isEmpty()) {
            xml.append("/>\n");
            return;
        }
        xml.append(">\n");
        content(xml, depth + 1, content);
        indent(xml, depth).append("</").append(tag).append(">\n");
    }

    private static void value(StringBuilder xml, int depth, DocumentValue value) {
        startTag(indent(xml, depth), value.tag(), value.attributes());
        if (value.text().isEmpty()) {
            xml.append("/>\n");
            return;
        }
        xml.append('>').append(XmlEscaping.text(value.text().get())).append("</").append(value.tag()).append(">\n");
    }

    private static void startTag(StringBuilder xml, String tag, Attributes attributes) {
        xml.append('<').append(tag);
        for (Attribute attribute : attributes) {
            xml.append(' ').append(attribute.name()).append("=\"")
                    .append(XmlEscaping.attribute(attribute.value())).append('"');
        }
    }

    private static StringBuilder indent(StringBuilder xml, int depth) {
        return xml.append(INDENT.repeat(depth));
    }

    /** Префикс корня — тот, под которым файл объявил пространство Archi. */
    private static String rootTag(Attributes attributes) {
        for (Attribute attribute : attributes) {
            if (attribute.value().equals(ModelDocument.NAMESPACE)) {
                if (attribute.name().equals("xmlns")) {
                    return "model";
                }
                if (attribute.name().startsWith("xmlns:")) {
                    return attribute.name().substring("xmlns:".length()) + ":model";
                }
            }
        }
        throw new IllegalArgumentException("у модели не объявлено пространство " + ModelDocument.NAMESPACE);
    }
}
