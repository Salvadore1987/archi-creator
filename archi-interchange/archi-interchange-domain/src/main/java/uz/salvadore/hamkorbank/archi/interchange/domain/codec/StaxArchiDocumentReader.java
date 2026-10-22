package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import static javax.xml.stream.XMLStreamConstants.CDATA;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.COMMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION;
import static javax.xml.stream.XMLStreamConstants.SPACE;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attributes;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentOrder;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;

/**
 * Потоковое чтение {@code .archimate} на StAX: дерево DOM не строится,
 * файл проходится один раз.
 *
 * <p>Что делает с файлом:
 * <ul>
 *   <li>идентификаторы берёт буквально, не перегенерирует;</li>
 *   <li>порядок содержимого фиксирует {@link DocumentOrder} — плотно, с нуля;</li>
 *   <li>атрибуты хранит все и в исходном порядке, знакомые и нет;</li>
 *   <li>элемент, которого не понимает, переносит {@link RawXmlFragment} с адресом
 *       родителя и позицией;</li>
 *   <li>{@code bounds} не пересчитывает: они уже относительны родителю, как в файле.</li>
 * </ul>
 *
 * <p>Повреждённые данные собираются все за один проход и бросаются
 * {@link CorruptDocumentException}: невалидный XML, узел без {@code id} или
 * {@code xsi:type}, корневая папка без {@code type}, узел не на своём месте в дереве
 * (папка в представлении, узел представления в папке), дубль идентификатора, ссылка
 * в пустоту, связь без конца, {@code targetConnections}, не совпадающий с соединениями узла. Если разбор
 * обрывается — XML ломается, корень не тот, вложенность глубже предела, — отказ
 * несёт дефекты, найденные до места обрыва, и сам обрыв; ссылки дальше обрыва
 * не проверить.
 *
 * <p>Комментарии и инструкции обработки между узлами и внутри значения (описания,
 * свойства) отбрасываются: Archi их не пишет, а круг без потерь обещает равенство
 * модели, а не байтов. Внутри непрозрачного фрагмента они сохраняются дословно.
 *
 * <p>Объявление {@code DOCTYPE} не отклоняется, а пропускается: DTD не читается,
 * внешние сущности не загружаются — файл приходит от пользователя.
 */
public final class StaxArchiDocumentReader implements ArchiDocumentReader {

    /**
     * Предел вложенности узлов модели: в файлах Archi глубина — десятки уровней, а без предела
     * рекурсивный разбор упирается в стек. Ниже умолчания JDK, чтобы отказ был дефектом файла,
     * а не сообщением парсера.
     */
    static final int MAX_DEPTH = 64;

    /** Какие узлы где лежат в файле Archi: вид родителя → теги детей. */
    private static final Map<Kind, Set<String>> PLACEMENT = Map.of(
            Kind.MODEL, Set.of("folder"),
            Kind.FOLDER, Set.of("folder", "element"),
            Kind.CONCEPT, Set.of(),
            Kind.DIAGRAM, Set.of("child"),
            Kind.DIAGRAM_NODE, Set.of("child", "sourceConnection"),
            Kind.CONNECTION, Set.of("sourceConnection"));

    /** Вид узла по месту в дереве: элемент и связь узлов не содержат, представление — содержит. */
    private enum Kind { MODEL, FOLDER, CONCEPT, DIAGRAM, DIAGRAM_NODE, CONNECTION }

    /** Родитель разбираемого узла: его вид и идентификатор ({@code null} — корень модели). */
    private record Place(Kind kind, String id) {
    }

    @Override
    public ModelDocument read(InputStream in) {
        Objects.requireNonNull(in, "in");
        Parse parse = null;
        try {
            XMLStreamReader xml = factory().createXMLStreamReader(in);
            try {
                parse = new Parse(xml);
                return parse.document();
            } finally {
                xml.close();
            }
        } catch (XMLStreamException e) {
            List<DocumentDefect> defects = parse == null ? new ArrayList<>() : new ArrayList<>(parse.defects);
            defects.add(malformed(e));
            throw new CorruptDocumentException(defects);
        }
    }

    private static XMLInputFactory factory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        return factory;
    }

    /**
     * Дефект испорченного XML — с местом ошибки, но без текста парсера: тот на языке JVM сервера
     * и говорит об устройстве разбора, а не о файле.
     */
    private static DocumentDefect malformed(XMLStreamException e) {
        Location location = e.getLocation();
        Message message = Message.of(InterchangeMessages.XML_UNREADABLE_NO_DETAIL);
        Integer line = location == null || location.getLineNumber() < 0 ? null : location.getLineNumber();
        Integer column = location == null || location.getColumnNumber() < 0 ? null : location.getColumnNumber();
        return new DocumentDefect(InterchangeCodes.MALFORMED_XML, message, null, line, column);
    }

    /** Состояние одного прохода по файлу. */
    private static final class Parse {

        private final XMLStreamReader xml;
        private final List<DocumentDefect> defects = new ArrayList<>();
        /** Все идентификаторы файла, включая лежащие внутри непрозрачных фрагментов. */
        private final Map<String, Integer> ids = new HashMap<>();
        private final List<Reference> references = new ArrayList<>();
        /** Цель соединения → соединения, которые к ней ведут, в порядке файла. */
        private final Map<String, List<String>> connectionsByTarget = new LinkedHashMap<>();
        /** Узел → его {@code targetConnections} как записано. */
        private final Map<String, Listed> listedTargetConnections = new LinkedHashMap<>();
        private int depth;
        private Place place = new Place(Kind.MODEL, null);

        Parse(XMLStreamReader xml) {
            this.xml = xml;
        }

        ModelDocument document() throws XMLStreamException {
            while (xml.next() != START_ELEMENT) {
                // пролог: объявление XML, комментарии, пробелы
            }
            if (!"model".equals(xml.getLocalName()) || !ModelDocument.NAMESPACE.equals(xml.getNamespaceURI())) {
                throw broken(DocumentDefect.at(InterchangeCodes.NOT_ARCHIMATE_MODEL,
                        Message.of(InterchangeMessages.NOT_ARCHIMATE_ROOT, qname(), ModelDocument.NAMESPACE),
                        null, line()));
            }
            int line = line();
            Attributes attributes = attributes();
            Optional<String> id = attributes.get("id");
            if (id.isEmpty()) {
                defect(InterchangeCodes.MISSING_ATTRIBUTE, Message.of(InterchangeMessages.MODEL_WITHOUT_ID), null,
                        line);
            } else if (!ArchiId.isValid(id.get())) {
                defect(InterchangeCodes.INVALID_ID, Message.of(InterchangeMessages.MODEL_ID_INVALID, id.get()),
                        id.get(), line);
            } else {
                register(id.get(), line);
            }
            List<DocumentContent> content = content(null);
            while (xml.hasNext()) {
                xml.next();
            }
            resolveReferences();
            checkTargetConnections();
            if (!defects.isEmpty()) {
                throw new CorruptDocumentException(defects);
            }
            return new ModelDocument(attributes, content);
        }

        /** Разбор оборван: отказ с дефектами, найденными до обрыва, и самим обрывом. */
        private CorruptDocumentException broken(DocumentDefect defect) {
            defects.add(defect);
            return new CorruptDocumentException(defects);
        }

        /** Содержимое текущего элемента до его закрывающего тега. */
        private List<DocumentContent> content(ArchiId parent) throws XMLStreamException {
            List<DocumentContent> content = new ArrayList<>();
            while (true) {
                switch (xml.next()) {
                    case START_ELEMENT -> {
                        DocumentOrder order = DocumentOrder.of(content.size());
                        DocumentContent item = item(parent, order);
                        if (item != null) {
                            content.add(item);
                        }
                    }
                    case END_ELEMENT -> {
                        return content;
                    }
                    case CHARACTERS, CDATA, SPACE -> {
                        if (!xml.getText().isBlank()) {
                            defect(InterchangeCodes.UNEXPECTED_TEXT,
                                    Message.of(InterchangeMessages.TEXT_OUTSIDE_VALUE, xml.getText().strip()),
                                    parent == null ? null : parent.value(), line());
                        }
                    }
                    default -> {
                        // комментарии и инструкции между узлами Archi не пишет; смысла в них нет
                    }
                }
            }
        }

        private DocumentContent item(ArchiId parent, DocumentOrder order) throws XMLStreamException {
            String prefix = xml.getPrefix();
            boolean unprefixed = prefix == null || prefix.isEmpty();
            String tag = xml.getLocalName();
            if (unprefixed && DocumentNode.TAGS.contains(tag)) {
                return node(tag, order);
            }
            if (unprefixed && DocumentValue.TAGS.contains(tag)) {
                return value(tag, parent, order);
            }
            return new RawXmlFragment(raw(), parent, order);
        }

        private DocumentNode node(String tag, DocumentOrder order) throws XMLStreamException {
            if (depth == MAX_DEPTH) {
                throw broken(DocumentDefect.at(InterchangeCodes.NESTING_TOO_DEEP,
                        Message.of(InterchangeMessages.NESTING_TOO_DEEP, MAX_DEPTH), null, line()));
            }
            depth++;
            try {
                return parsedNode(tag, order);
            } finally {
                depth--;
            }
        }

        private DocumentNode parsedNode(String tag, DocumentOrder order) throws XMLStreamException {
            int line = line();
            Attributes attributes = attributes();
            Optional<String> id = attributes.get("id");
            if (id.isEmpty() || !ArchiId.isValid(id.get())) {
                if (id.isEmpty()) {
                    defect(InterchangeCodes.MISSING_ATTRIBUTE, Message.of(InterchangeMessages.NODE_WITHOUT_ID, tag),
                            null, line);
                } else {
                    defect(InterchangeCodes.INVALID_ID, Message.of(InterchangeMessages.ID_INVALID, id.get()),
                            id.get(), line);
                }
                skipRest();
                return null;
            }
            register(id.get(), line);
            inspect(tag, id.get(), attributes, line);
            placed(tag, id.get(), attributes, line);
            Place parent = place;
            place = new Place(kind(tag, attributes), id.get());
            try {
                return new DocumentNode(tag, order, attributes, content(ArchiId.of(id.get())));
            } finally {
                place = parent;
            }
        }

        /** Узел лежит там, где его кладёт Archi; у корневой папки есть тип. */
        private void placed(String tag, String id, Attributes attributes, int line) {
            if (!PLACEMENT.get(place.kind()).contains(tag)) {
                defect(InterchangeCodes.MISPLACED_NODE,
                        Message.of(InterchangeMessages.UNEXPECTED_NODE, tag, id, where(place)), id, line);
            }
            if (place.kind() == Kind.MODEL && tag.equals("folder")
                    && attributes.get("type").filter(t -> !t.isBlank()).isEmpty()) {
                defect(InterchangeCodes.MISSING_ATTRIBUTE, Message.of(InterchangeMessages.ROOT_FOLDER_UNTYPED, id),
                        id, line);
            }
        }

        private static Kind kind(String tag, Attributes attributes) {
            return switch (DocumentNode.kind(tag, attributes)) {
                case FOLDER -> Kind.FOLDER;
                case VIEW_NODE -> Kind.DIAGRAM_NODE;
                case VIEW_EDGE -> Kind.CONNECTION;
                case VIEW -> Kind.DIAGRAM;
                case ELEMENT, RELATIONSHIP -> Kind.CONCEPT;
            };
        }

        private static Message where(Place place) {
            return switch (place.kind()) {
                case MODEL -> Message.of(InterchangeMessages.WHERE_MODEL_ROOT);
                case FOLDER -> Message.of(InterchangeMessages.WHERE_FOLDER);
                case CONCEPT -> Message.of(InterchangeMessages.WHERE_INSIDE, place.id());
                case DIAGRAM -> Message.of(InterchangeMessages.WHERE_VIEW);
                case DIAGRAM_NODE -> Message.of(InterchangeMessages.WHERE_VIEW_NODE);
                case CONNECTION -> Message.of(InterchangeMessages.WHERE_VIEW_EDGE);
            };
        }

        /** Обязательные атрибуты и ссылки узла — по тегу. */
        private void inspect(String tag, String id, Attributes attributes, int line) {
            if (tag.equals("folder")) {
                return;
            }
            Optional<String> type = attributes.get("xsi:type");
            if (type.isEmpty()) {
                defect(InterchangeCodes.MISSING_ATTRIBUTE, Message.of(InterchangeMessages.NODE_WITHOUT_TYPE, tag),
                        id, line);
            }
            switch (tag) {
                case "element" -> {
                    if (DocumentNode.kind(tag, attributes) == DocumentNode.Kind.RELATIONSHIP) {
                        end(id, attributes, "source", line);
                        end(id, attributes, "target", line);
                    }
                }
                case "child" -> {
                    reference(id, attributes, "archimateElement", line);
                    reference(id, attributes, "model", line);
                }
                case "sourceConnection" -> {
                    end(id, attributes, "source", line);
                    end(id, attributes, "target", line);
                    reference(id, attributes, "archimateRelationship", line);
                    attributes.get("target").ifPresent(target ->
                            connectionsByTarget.computeIfAbsent(target, t -> new ArrayList<>()).add(id));
                }
                // недостижимо — сюда приходят только теги узлов документа
                default -> throw new IllegalStateException(tag);
            }
            attributes.get("targetConnections").ifPresent(listed -> {
                List<String> tokens = Arrays.stream(listed.trim().split("\\s+")).filter(s -> !s.isEmpty()).toList();
                tokens.forEach(t -> references.add(new Reference(id, "targetConnections", t, line)));
                listedTargetConnections.put(id, new Listed(tokens, line));
            });
        }

        private void end(String id, Attributes attributes, String attribute, int line) {
            if (attributes.get(attribute).filter(v -> !v.isBlank()).isEmpty()) {
                defect(InterchangeCodes.MISSING_END,
                        Message.of(InterchangeMessages.RELATIONSHIP_WITHOUT_END, attribute), id, line);
            } else {
                reference(id, attributes, attribute, line);
            }
        }

        private void reference(String id, Attributes attributes, String attribute, int line) {
            attributes.get(attribute).ifPresent(target -> references.add(new Reference(id, attribute, target, line)));
        }

        private DocumentContent value(String tag, ArchiId parent, DocumentOrder order)
                throws XMLStreamException {
            Attributes attributes = attributes();
            StringBuilder text = null;
            while (true) {
                switch (xml.next()) {
                    case CHARACTERS, CDATA, SPACE -> {
                        text = text == null ? new StringBuilder() : text;
                        text.append(xml.getText());
                    }
                    case START_ELEMENT -> {
                        // Значение с вложенной разметкой — не то, что знает кодек: целиком во фрагмент.
                        StringBuilder out = new StringBuilder();
                        startTag(out, tag, attributes);
                        out.append('>');
                        if (text != null) {
                            out.append(XmlEscaping.text(text.toString()));
                        }
                        startTag(out);
                        rest(out, true, 2);
                        return new RawXmlFragment(out.toString(), parent, order);
                    }
                    case END_ELEMENT -> {
                        return new DocumentValue(tag, order, attributes, text == null ? null : text.toString());
                    }
                    default -> {
                        // комментарий внутри значения
                    }
                }
            }
        }

        /** Текущий элемент со всем поддеревом — разметкой, с внутренними пробелами как в файле. */
        private String raw() throws XMLStreamException {
            StringBuilder out = new StringBuilder();
            startTag(out);
            rest(out, true, 1);
            return out.toString();
        }

        private void skipRest() throws XMLStreamException {
            rest(new StringBuilder(), true, 1);
        }

        /**
         * Дописывает содержимое до закрытия элемента глубины {@code depth}.
         * Пустой элемент закрывается {@code />}: какой из двух равнозначных видов
         * был в файле, StAX не сообщает.
         */
        private void rest(StringBuilder out, boolean startTagOpen, int depth) throws XMLStreamException {
            boolean open = startTagOpen;
            while (depth > 0) {
                int event = xml.next();
                if (open && event != END_ELEMENT) {
                    out.append('>');
                    open = false;
                }
                switch (event) {
                    case START_ELEMENT -> {
                        startTag(out);
                        open = true;
                        depth++;
                    }
                    case END_ELEMENT -> {
                        if (open) {
                            out.append("/>");
                            open = false;
                        } else {
                            out.append("</").append(qname()).append('>');
                        }
                        depth--;
                    }
                    case CHARACTERS, CDATA, SPACE -> out.append(XmlEscaping.text(xml.getText()));
                    case COMMENT -> out.append("<!--").append(xml.getText()).append("-->");
                    case PROCESSING_INSTRUCTION -> {
                        out.append("<?").append(xml.getPITarget());
                        if (xml.getPIData() != null && !xml.getPIData().isEmpty()) {
                            out.append(' ').append(xml.getPIData());
                        }
                        out.append("?>");
                    }
                    default -> {
                        // ENTITY_REFERENCE при IS_COALESCING не приходит
                    }
                }
            }
        }

        private void startTag(StringBuilder out) {
            int line = line();
            Attributes attributes = attributes();
            // Идентификаторы внутри фрагмента — тоже идентификаторы файла: на них можно сослаться,
            // и они не должны совпасть с чужими.
            attributes.get("id").ifPresent(id -> register(id, line));
            startTag(out, qname(), attributes);
        }

        private static void startTag(StringBuilder out, String qname, Attributes attributes) {
            out.append('<').append(qname);
            for (Attribute attribute : attributes) {
                out.append(' ').append(attribute.name()).append("=\"")
                        .append(XmlEscaping.attribute(attribute.value())).append('"');
            }
        }

        /** Объявления пространств имён (первыми) и атрибуты текущего элемента, в порядке файла. */
        private Attributes attributes() {
            List<Attribute> list = new ArrayList<>();
            for (int i = 0; i < xml.getNamespaceCount(); i++) {
                String prefix = xml.getNamespacePrefix(i);
                String name = prefix == null || prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
                list.add(new Attribute(name, Objects.requireNonNullElse(xml.getNamespaceURI(i), "")));
            }
            for (int i = 0; i < xml.getAttributeCount(); i++) {
                String prefix = xml.getAttributePrefix(i);
                String local = xml.getAttributeLocalName(i);
                String name = prefix == null || prefix.isEmpty() ? local : prefix + ":" + local;
                list.add(new Attribute(name, xml.getAttributeValue(i)));
            }
            return new Attributes(list);
        }

        private String qname() {
            String prefix = xml.getPrefix();
            return prefix == null || prefix.isEmpty() ? xml.getLocalName() : prefix + ":" + xml.getLocalName();
        }

        private int line() {
            return xml.getLocation() == null ? -1 : xml.getLocation().getLineNumber();
        }

        private void register(String id, int line) {
            Integer previous = ids.putIfAbsent(id, line);
            if (previous != null) {
                defect(InterchangeCodes.DUPLICATE_ID, Message.of(InterchangeMessages.ID_REPEATED, id, previous),
                        id, line);
            }
        }

        private void resolveReferences() {
            for (Reference reference : references) {
                if (!ids.containsKey(reference.target())) {
                    defect(InterchangeCodes.DANGLING_REFERENCE,
                            Message.of(InterchangeMessages.DANGLING_REFERENCE, reference.attribute(),
                                    reference.target()),
                            reference.owner(), reference.line());
                }
            }
        }

        /**
         * {@code targetConnections} — производная величина: множество обязано совпасть
         * с соединениями, ведущими к узлу. Порядок не проверяется — он хранится как есть.
         */
        private void checkTargetConnections() {
            listedTargetConnections.forEach((owner, listed) -> {
                Set<String> expected = new LinkedHashSet<>(connectionsByTarget.getOrDefault(owner, List.of()));
                if (!expected.equals(new HashSet<>(listed.tokens()))) {
                    defect(InterchangeCodes.TARGET_CONNECTIONS_MISMATCH,
                            Message.of(InterchangeMessages.TARGET_CONNECTIONS_DIFFER, listed.tokens(), expected),
                            owner, listed.line());
                }
            });
            connectionsByTarget.forEach((target, connections) -> {
                if (!listedTargetConnections.containsKey(target) && ids.containsKey(target)) {
                    defect(InterchangeCodes.TARGET_CONNECTIONS_MISMATCH,
                            Message.of(InterchangeMessages.TARGET_CONNECTIONS_MISSING, connections), target,
                            ids.get(target));
                }
            });
        }

        private void defect(String code, Message message, String archiId, int line) {
            defects.add(DocumentDefect.at(code, message, archiId, line));
        }
    }

    private record Reference(String owner, String attribute, String target, int line) {
    }

    private record Listed(List<String> tokens, int line) {
    }
}
