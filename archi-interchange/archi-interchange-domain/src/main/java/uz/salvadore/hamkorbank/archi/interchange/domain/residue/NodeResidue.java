package uz.salvadore.hamkorbank.archi.interchange.domain.residue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Остаток XML одного объекта: порядок атрибутов с метками типизированных,
 * незнакомые атрибуты, содержимое без идентификатора с позициями.
 *
 * <p>Строковая форма — маленький XML: формат свой, закрытый, и читается тем же StAX,
 * что и {@code .archimate}, без новых зависимостей в домене.
 * <pre>{@code
 * <r><a n="xsi:type" typed="1"/><a n="vendor:owner">Розница</a>
 *    <s o="1000" t="documentation"/>
 *    <v o="2000" t="content"><x>Идея</x></v>
 *    <f o="3000">&lt;feature name="iconVisible" value="false"/&gt;</f></r>
 * }</pre>
 */
public record NodeResidue(List<ResidueAttribute> attributes, List<ResidueItem> items) {

    /** Остаток без атрибутов и содержимого. */
    public static final NodeResidue EMPTY = new NodeResidue(List.of(), List.of());

    public NodeResidue {
        attributes = List.copyOf(attributes);
        items = List.copyOf(items);
    }

    /** Остатка нет: ни атрибутов, ни содержимого. */
    public boolean empty() {
        return attributes.isEmpty() && items.isEmpty();
    }

    /** Атрибут остатка по имени. */
    public Optional<ResidueAttribute> attribute(String name) {
        return attributes.stream().filter(a -> a.name().equals(name)).findFirst();
    }

    private static String attribute(XMLStreamReader xml, String name) {
        String value = xml.getAttributeValue(null, name);
        if (value == null) {
            throw new InvalidValueException(InterchangeMessages.RESIDUE_ATTRIBUTE_MISSING, xml.getLocalName(), name);
        }
        return value;
    }

    /** Места данного тега по порядку. */
    public List<ResidueItem.Slot> slots(String tag) {
        return items.stream().filter(i -> i instanceof ResidueItem.Slot s && s.tag().equals(tag))
                .map(ResidueItem.Slot.class::cast).toList();
    }

    /** Строковая форма остатка для хранения. */
    public String encode() {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(out);
            try {
                write(xml);
            } finally {
                xml.close();
            }
        } catch (XMLStreamException e) {
            // недостижимо — запись XML в строку не отказывает
            throw new IllegalStateException(Message.of(InterchangeMessages.RESIDUE_UNWRITABLE).toString(), e);
        }
        return out.toString();
    }

    private void write(XMLStreamWriter xml) throws XMLStreamException {
        xml.writeStartElement("r");
        writeAttributes(xml, attributes);
        for (ResidueItem item : items) {
            switch (item) {
                case ResidueItem.Slot slot -> {
                    xml.writeStartElement("s");
                    xml.writeAttribute("o", Long.toString(slot.order()));
                    xml.writeAttribute("t", slot.tag());
                    writeAttributes(xml, slot.attributes());
                    xml.writeEndElement();
                }
                case ResidueItem.Value value -> {
                    xml.writeStartElement("v");
                    xml.writeAttribute("o", Long.toString(value.order()));
                    xml.writeAttribute("t", value.tag());
                    writeAttributes(xml, value.attributes());
                    if (value.text() != null) {
                        xml.writeStartElement("x");
                        writeText(xml, value.text());
                        xml.writeEndElement();
                    }
                    xml.writeEndElement();
                }
                case ResidueItem.Fragment fragment -> {
                    xml.writeStartElement("f");
                    xml.writeAttribute("o", Long.toString(fragment.order()));
                    writeText(xml, fragment.xml());
                    xml.writeEndElement();
                }
            }
        }
        xml.writeEndElement();
    }

    /** Остаток из строковой формы. */
    public static NodeResidue decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.IS_COALESCING, true);
            XMLStreamReader xml = factory.createXMLStreamReader(new StringReader(encoded));
            try {
                return read(xml);
            } finally {
                xml.close();
            }
        } catch (XMLStreamException | InvalidValueException | NumberFormatException e) {
            InterchangeDomainException corrupted = new InterchangeDomainException(InterchangeCodes.RESIDUE_CORRUPTED,
                    Message.of(InterchangeMessages.RESIDUE_CORRUPTED));
            corrupted.initCause(e);
            throw corrupted;
        }
    }

    private static NodeResidue read(XMLStreamReader xml) throws XMLStreamException {
        List<ResidueAttribute> attributes = new ArrayList<>();
        List<ResidueItem> items = new ArrayList<>();
        xml.nextTag();
        require(xml, "r");
        while (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
            switch (xml.getLocalName()) {
                case "a" -> attributes.add(readAttribute(xml));
                case "s" -> items.add(readSlot(xml));
                case "v" -> items.add(readValue(xml));
                case "f" -> items.add(new ResidueItem.Fragment(order(xml), xml.getElementText()));
                default -> throw new InvalidValueException(InterchangeMessages.RESIDUE_UNKNOWN_RECORD,
                        xml.getLocalName());
            }
        }
        return new NodeResidue(attributes, items);
    }

    private static ResidueAttribute readAttribute(XMLStreamReader xml) throws XMLStreamException {
        String name = attribute(xml, "n");
        boolean typed = "1".equals(xml.getAttributeValue(null, "typed"));
        String text = xml.getElementText();
        return typed ? ResidueAttribute.placeholder(name) : ResidueAttribute.literal(name, text);
    }

    private static ResidueItem.Slot readSlot(XMLStreamReader xml) throws XMLStreamException {
        long order = order(xml);
        String tag = attribute(xml, "t");
        List<ResidueAttribute> attributes = new ArrayList<>();
        while (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
            attributes.add(readAttribute(xml));
        }
        return new ResidueItem.Slot(order, tag, attributes);
    }

    private static ResidueItem.Value readValue(XMLStreamReader xml) throws XMLStreamException {
        long order = order(xml);
        String tag = attribute(xml, "t");
        List<ResidueAttribute> attributes = new ArrayList<>();
        String text = null;
        while (xml.nextTag() == XMLStreamConstants.START_ELEMENT) {
            if (xml.getLocalName().equals("x")) {
                text = xml.getElementText();
            } else {
                attributes.add(readAttribute(xml));
            }
        }
        return new ResidueItem.Value(order, tag, attributes, text);
    }

    private static void writeAttributes(XMLStreamWriter xml, List<ResidueAttribute> attributes)
            throws XMLStreamException {
        for (ResidueAttribute attribute : attributes) {
            xml.writeStartElement("a");
            xml.writeAttribute("n", attribute.name());
            if (attribute.value() != null) {
                writeText(xml, attribute.value());
            } else {
                xml.writeAttribute("typed", "1");
            }
            xml.writeEndElement();
        }
    }

    /**
     * Значения — текстом элемента, а не атрибутом: перевод строки в атрибуте парсер
     * нормализует в пробел. {@code \r} парсер нормализует и в тексте, поэтому он идёт
     * ссылкой на символ.
     */
    private static void writeText(XMLStreamWriter xml, String text) throws XMLStreamException {
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\r') {
                xml.writeCharacters(text.substring(start, i));
                xml.writeEntityRef("#xD");
                start = i + 1;
            }
        }
        xml.writeCharacters(text.substring(start));
    }

    private static long order(XMLStreamReader xml) {
        return Long.parseLong(attribute(xml, "o"));
    }

    private static void require(XMLStreamReader xml, String name) {
        if (!xml.getLocalName().equals(name)) {
            throw new InvalidValueException(InterchangeMessages.RESIDUE_RECORD_EXPECTED, name);
        }
    }
}
