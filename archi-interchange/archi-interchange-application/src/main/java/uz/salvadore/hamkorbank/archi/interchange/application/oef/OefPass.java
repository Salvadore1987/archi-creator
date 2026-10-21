package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.attr;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.indent;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.langString;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.ref;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportLoss;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossObjectKind;

/**
 * Один проход писателя: документ, отчёт о потерях, словарь свойств и множества выгруженных
 * объектов — общее для частей писателя.
 */
final class OefPass {

    private static final Pattern FRAGMENT_TAG = Pattern.compile("^<([^\\s/>]+)");

    private final ModelDocument document;
    private final List<ExportLoss> losses = new ArrayList<>();
    private final Map<String, String> propertyIds = new LinkedHashMap<>();
    private final Set<String> usedIds = new HashSet<>();
    private final Set<String> concepts = new HashSet<>();
    private final Set<String> diagrams = new HashSet<>();
    private final Map<String, DocumentNode> byId = new HashMap<>();
    private int nextProperty = 1;

    OefPass(ModelDocument document) {
        this.document = document;
        usedIds.add(ref(document.archiId().value()));
        for (DocumentNode node : document.allNodes()) {
            byId.put(node.archiId().value(), node);
            usedIds.add(ref(node.archiId().value()));
        }
    }

    /** Документ прохода. */
    ModelDocument document() {
        return document;
    }

    /** Отчёт о потерях в порядке обхода документа. */
    List<ExportLoss> losses() {
        return losses;
    }

    /** Выгруженные элементы и связи модели — пополняются по ходу прохода. */
    Set<String> concepts() {
        return concepts;
    }

    /** Выгружаемые представления-диаграммы. */
    Set<String> diagrams() {
        return diagrams;
    }

    /** Узлы документа по идентификатору. */
    Map<String, DocumentNode> byId() {
        return byId;
    }

    /** Словарь свойств: ключ — идентификатор определения в порядке первого появления. */
    Map<String, String> propertyIds() {
        return propertyIds;
    }

    /** Каждый атрибут вне списка переносимых — запись отчёта. */
    void checkAttributes(DocumentNode node, String kind, Set<String> carried) {
        for (Attribute attribute : node.attributes()) {
            if (!carried.contains(attribute.name()) && !attribute.name().startsWith("xmlns")) {
                attributeLoss(node.archiId().value(), kind, attribute);
            }
        }
    }

    /** Остаток XML и значения вне списка переносимых — записи отчёта; вложенные узлы — не здесь. */
    void checkContent(DocumentNode node, String kind, Set<String> carried) {
        String id = node.archiId().value();
        for (DocumentContent item : node.content()) {
            switch (item) {
                case RawXmlFragment raw -> rawLoss(id, kind, raw);
                case DocumentValue value when carried.contains(value.tag()) -> {
                }
                case DocumentValue value when value.tag().equals("property") -> {
                    String key = value.attribute("key").orElse("");
                    loss(id, kind, "property:" + key, Message.of(InterchangeMessages.OEF_LOSS_PROPERTY, key));
                }
                case DocumentValue value -> loss(id, kind, "value:" + value.tag(),
                        Message.of(InterchangeMessages.OEF_LOSS_VALUE, value.tag()));
                case DocumentNode child -> {
                }
            }
        }
    }

    void properties(StringBuilder out, int depth, List<DocumentValue> properties) {
        if (properties.isEmpty()) {
            return;
        }
        indent(out, depth).append("<properties>\n");
        for (DocumentValue property : properties) {
            String key = property.attribute("key").orElse("");
            indent(out, depth + 1).append("<property propertyDefinitionRef=\"").append(attr(propertyId(key)))
                    .append("\">\n");
            langString(out, depth + 2, "value", property.attribute("value").orElse(""));
            indent(out, depth + 1).append("</property>\n");
        }
        indent(out, depth).append("</properties>\n");
    }

    /** Ключ свойства — запись общего словаря: {@code propid-N} в порядке первого появления. */
    String propertyId(String key) {
        return propertyIds.computeIfAbsent(key, k -> {
            String id;
            do {
                id = "propid-" + nextProperty++;
            } while (usedIds.contains(id));
            usedIds.add(id);
            return id;
        });
    }

    void documentation(StringBuilder out, int depth, DocumentNode node) {
        node.documentation().filter(d -> !d.isEmpty()).ifPresent(d -> langString(out, depth, "documentation", d));
    }

    /**
     * Идентификатор объекта в файле. Не годный для {@code xs:ID} заменяется
     * производным от него самого — замена в отчёте; ссылки на объект берут ту же замену.
     */
    String identifier(String archiId, String kind) {
        String id = ref(archiId);
        if (!id.equals(archiId)) {
            loss(archiId, kind, "identifier", Message.of(InterchangeMessages.OEF_LOSS_IDENTIFIER, archiId, id));
        }
        return id;
    }

    void attributeLoss(String archiId, String kind, Attribute attribute) {
        loss(archiId, kind, "attribute:" + attribute.name(),
                Message.of(InterchangeMessages.OEF_LOSS_ATTRIBUTE, attribute.name(), attribute.value()));
    }

    void styleLoss(String archiId, String kind, String name, String value) {
        loss(archiId, kind, "style:" + name, Message.of(InterchangeMessages.OEF_LOSS_STYLE, name, value));
    }

    void rawLoss(String archiId, String kind, RawXmlFragment raw) {
        Matcher tag = FRAGMENT_TAG.matcher(raw.xml());
        String name = tag.find() ? tag.group(1) : raw.xml();
        loss(archiId, kind, "xml:" + name, Message.of(InterchangeMessages.OEF_LOSS_RAW, name));
    }

    void loss(String archiId, String kind, String aspect, Message reason) {
        losses.add(new ExportLoss(archiId, LossObjectKind.of(kind), aspect, reason));
    }
}
