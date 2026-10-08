package uz.salvadore.hamkorbank.archi.bootstrap.roundtrip;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.opentest4j.AssertionFailedError;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Семантическое сравнение XML для golden-file тестов (docs/backend.md §9.1).
 *
 * <p>Прощает только форматирование: отступы между элементами, порядок атрибутов
 * (в XML он смысла не несёт), {@code <a/>} против {@code <a></a>}, префиксы при
 * тех же пространствах имён, комментарии и текст из одних пробелов — он везде
 * форматирование, в том числе в пустой папке, записанной как {@code <folder>},
 * перевод строки, {@code </folder>}. Строго проверяет всё остальное: порядок
 * дочерних узлов, имена, значения атрибутов, включая {@code id}, и непробельный
 * текст — дословно, с краевыми пробелами.
 *
 * <p>Расхождение сообщается путём до места и обеими версиями: «где-то не сошлось»
 * на файле в 4 000 строк не помогает.
 */
public final class XmlEquivalence {

    private XmlEquivalence() {
    }

    public static void assertXmlEquivalent(byte[] expected, byte[] actual) {
        Element left = parse(expected, "ожидаемый");
        Element right = parse(actual, "фактический");
        compare(left, right, "/" + name(left));
    }

    private static void compare(Element expected, Element actual, String path) {
        if (!Objects.equals(expected.getNamespaceURI(), actual.getNamespaceURI())
                || !expected.getLocalName().equals(actual.getLocalName())) {
            fail(path, "элемент " + name(expected), "элемент " + name(actual));
        }
        Map<String, String> expectedAttributes = attributes(expected);
        Map<String, String> actualAttributes = attributes(actual);
        if (!expectedAttributes.equals(actualAttributes)) {
            fail(path, "атрибуты " + expectedAttributes, "атрибуты " + actualAttributes);
        }
        List<Object> expectedContent = content(expected);
        List<Object> actualContent = content(actual);
        int elementIndex = 0;
        for (int i = 0; i < Math.max(expectedContent.size(), actualContent.size()); i++) {
            if (i >= expectedContent.size()) {
                fail(path, "конец содержимого", "лишнее " + describe(actualContent.get(i)));
            }
            if (i >= actualContent.size()) {
                fail(path, describe(expectedContent.get(i)), "конец содержимого");
            }
            Object left = expectedContent.get(i);
            Object right = actualContent.get(i);
            if (left instanceof Element leftElement && right instanceof Element rightElement) {
                elementIndex++;
                compare(leftElement, rightElement, path + "/" + name(leftElement) + "[" + elementIndex + "]"
                        + leftElement.getAttribute("id").transform(id -> id.isEmpty() ? "" : "[@id=" + id + "]"));
            } else if (!left.equals(right) || left.getClass() != right.getClass()) {
                fail(path, describe(left), describe(right));
            }
        }
    }

    /** Значимое содержимое: элементы и непробельный текст, в порядке документа. */
    private static List<Object> content(Element element) {
        NodeList nodes = element.getChildNodes();
        List<Object> content = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            switch (node.getNodeType()) {
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> text.append(node.getNodeValue());
                case Node.ELEMENT_NODE -> {
                    flush(text, content);
                    content.add(node);
                }
                default -> {
                    // комментарии и инструкции обработки смысла модели не несут
                }
            }
        }
        flush(text, content);
        return content;
    }

    private static void flush(StringBuilder text, List<Object> content) {
        if (!text.toString().isBlank()) {
            content.add(text.toString());
        }
        text.setLength(0);
    }

    private static Map<String, String> attributes(Element element) {
        Map<String, String> attributes = new TreeMap<>();
        NamedNodeMap map = element.getAttributes();
        for (int i = 0; i < map.getLength(); i++) {
            Attr attribute = (Attr) map.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                continue;
            }
            String namespace = attribute.getNamespaceURI() == null ? "" : "{" + attribute.getNamespaceURI() + "}";
            attributes.put(namespace + attribute.getLocalName(), attribute.getValue());
        }
        return attributes;
    }

    private static Element parse(byte[] xml, String which) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml)).getDocumentElement();
        } catch (Exception e) {
            throw new AssertionFailedError(which + " XML не разбирается: " + e.getMessage(), e);
        }
    }

    private static String name(Element element) {
        return element.getTagName();
    }

    private static String describe(Object item) {
        return item instanceof Element element ? "элемент <" + name(element) + ">" : "текст «" + item + "»";
    }

    private static void fail(String path, String expected, String actual) {
        throw new AssertionFailedError("XML расходится в " + path + ": ожидалось " + expected + ", получено " + actual,
                expected, actual);
    }
}
