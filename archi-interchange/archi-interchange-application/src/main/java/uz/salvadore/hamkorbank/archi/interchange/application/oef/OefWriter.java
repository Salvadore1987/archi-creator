package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.attr;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.close;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.endpointsClosure;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.indent;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.langString;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.ref;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.section;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.union;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportLoss;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentContent;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.RawXmlFragment;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;

/**
 * Документ {@code .archimate} в ArchiMate Model Exchange File Format 3.1 и отчёт о том,
 * что не перенеслось.
 *
 * <p>Переносится всё, что формат умеет выразить, независимо от того, поддержан ли тип
 * в редакторе: у {@code Location} или {@code Grouping} в OEF есть свои типы. Не переносится
 * то, чего в OEF нет, — остаток XML, представления не-ArchiMate, часть оформления.
 *
 * <p>Отчёт полон по построению: у каждого вида объекта закрытый список атрибутов
 * и значений, которые писатель переносит, и всё, что в него не входит, становится
 * записью отчёта. Атрибут, который Archi заведёт завтра, окажется в отчёте сам.
 *
 * <p>Геометрия в OEF абсолютная: {@code bounds} складываются от корня представления,
 * размер {@code -1} подставляется по типу, как на канве, точки перегиба переводятся
 * из смещений от центров в координаты той же формулой, что у канвы. Отрицательных
 * координат формат не допускает — такое представление сдвигается целиком.
 *
 * <p>Результат детерминирован: один документ — одни и те же байты.
 */
public final class OefWriter {

    /** Язык текстов модели: формат многоязычен, мы пишем один язык. */
    public static final String LANGUAGE = "ru";

    private static final String PREFIX = "archimate:";
    private static final String DIAGRAM = "archimate:ArchimateDiagramModel";
    private static final String JUNCTION = "archimate:Junction";
    /** Окончание типа связи Archi; без него — имя типа в OEF. */
    private static final String RELATIONSHIP_SUFFIX = "Relationship";

    /** Типы элементов OEF 3.1 без соединителей — перечень схемы. */
    private static final Set<String> ELEMENT_TYPES = Set.of("BusinessActor", "BusinessRole",
            "BusinessCollaboration", "BusinessInterface", "BusinessProcess", "BusinessFunction", "BusinessInteraction",
            "BusinessEvent", "BusinessService", "BusinessObject", "Contract", "Representation", "Product",
            "ApplicationComponent", "ApplicationCollaboration", "ApplicationInterface", "ApplicationFunction",
            "ApplicationInteraction", "ApplicationProcess", "ApplicationEvent", "ApplicationService", "DataObject",
            "Node", "Device", "SystemSoftware", "TechnologyCollaboration", "TechnologyInterface", "Path",
            "CommunicationNetwork", "TechnologyFunction", "TechnologyProcess", "TechnologyInteraction",
            "TechnologyEvent", "TechnologyService", "Artifact", "Equipment", "Facility", "DistributionNetwork",
            "Material", "Stakeholder", "Driver", "Assessment", "Goal", "Outcome", "Principle", "Requirement",
            "Constraint", "Meaning", "Value", "Resource", "Capability", "CourseOfAction", "ValueStream", "WorkPackage",
            "Deliverable", "ImplementationEvent", "Plateau", "Gap", "Grouping", "Location");
    private static final Set<String> RELATIONSHIP_TYPES = Set.of("Composition", "Aggregation", "Assignment",
            "Realization", "Serving", "Access", "Influence", "Triggering", "Flow", "Specialization", "Association");
    private static final Map<AccessType, String> ACCESS = Map.of(AccessType.WRITE, "Write", AccessType.READ, "Read",
            AccessType.ACCESS, "Access", AccessType.READ_WRITE, "ReadWrite");
    private static final Set<String> MODEL_ATTRIBUTES = Set.of("name", "id", "version");
    private static final Set<String> FOLDER_ATTRIBUTES = Set.of("name", "id");
    private static final Set<String> ELEMENT_ATTRIBUTES = Set.of(OefMarkup.XSI_TYPE, "name", "id");

    private final Notation notation;

    /** @param notation размеры по умолчанию для типов — те же, что у канвы */
    public OefWriter(Notation notation) {
        this.notation = notation;
    }

    /** Файл OEF в UTF-8 и всё, что в него не перенеслось, в порядке обхода документа. */
    public record Written(byte[] xml, List<ExportLoss> losses) {

        public Written {
            xml = xml.clone();
            losses = List.copyOf(losses);
        }

        @Override
        public byte[] xml() {
            return xml.clone();
        }
    }

    /** Документ модели в Open Exchange Format вместе с отчётом о потерях. */
    public Written write(ModelDocument document) {
        return new Run(document).write();
    }

    /** Проход по концептам и папкам; общее состояние — в {@link OefPass}, представления — {@link OefViews}. */
    private final class Run {

        private final OefPass pass;
        private final ModelDocument document;
        private final String modelId;

        Run(ModelDocument document) {
            this.pass = new OefPass(document);
            this.document = document;
            this.modelId = document.archiId().value();
        }

        Written write() {
            StringBuilder model = new StringBuilder();
            model.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            model.append("<model xmlns=\"").append(OefSchema.NAMESPACE)
                    .append("\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"")
                    .append(OefSchema.NAMESPACE).append(' ').append(OefSchema.LOCATION).append("\" identifier=\"")
                    .append(attr(pass.identifier(modelId, "model"))).append("\">\n");
            modelHeader(model);

            StringBuilder elements = elements();
            StringBuilder relationships = relationships();
            document.views().stream().filter(v -> v.archiType().filter(DIAGRAM::equals).isPresent())
                    .forEach(v -> pass.diagrams().add(v.archiId().value()));
            StringBuilder views = new OefViews(pass, notation).write();
            StringBuilder organizations = organizations();

            model.append(elements).append(relationships).append(organizations);
            if (!pass.propertyIds().isEmpty()) {
                model.append("  <propertyDefinitions>\n");
                pass.propertyIds().forEach((key, id) -> {
                    model.append("    <propertyDefinition identifier=\"").append(attr(id))
                            .append("\" type=\"string\">\n");
                    langString(model, 3, "name", key);
                    model.append("    </propertyDefinition>\n");
                });
                model.append("  </propertyDefinitions>\n");
            }
            model.append(views).append("</model>\n");
            return new Written(model.toString().getBytes(StandardCharsets.UTF_8), pass.losses());
        }

        // ── модель ───────────────────────────────────────────────────────

        private void modelHeader(StringBuilder out) {
            for (Attribute attribute : document.attributes()) {
                if (!MODEL_ATTRIBUTES.contains(attribute.name()) && !attribute.name().startsWith("xmlns")) {
                    pass.attributeLoss(modelId, "model", attribute);
                }
            }
            langString(out, 1, "name", document.name());
            document.documentation().ifPresent(text -> langString(out, 1, "documentation", text));
            List<DocumentValue> properties = new ArrayList<>();
            for (DocumentContent item : document.content()) {
                switch (item) {
                    case DocumentValue value when value.tag().equals("property") -> properties.add(value);
                    case DocumentValue value when value.tag().equals("purpose") -> {
                    }
                    case DocumentValue value -> pass.loss(modelId, "model", "value:" + value.tag(),
                            Message.of(InterchangeMessages.OEF_LOSS_VALUE, value.tag()));
                    case RawXmlFragment raw -> pass.rawLoss(modelId, "model", raw);
                    case DocumentNode node -> {
                    }
                }
            }
            pass.properties(out, 1, properties);
        }

        // ── элементы и связи ─────────────────────────────────────────────

        private StringBuilder elements() {
            StringBuilder out = new StringBuilder();
            for (DocumentNode element : document.elements()) {
                String id = element.archiId().value();
                Optional<String> type = elementType(element);
                if (type.isEmpty()) {
                    pass.loss(id, "element", "type",
                            Message.of(InterchangeMessages.OEF_LOSS_CONCEPT_TYPE, element.archiType().orElse("")));
                    continue;
                }
                pass.concepts().add(id);
                Set<String> carried = element.archiType().filter(JUNCTION::equals).isPresent()
                        ? union(ELEMENT_ATTRIBUTES, "type") : ELEMENT_ATTRIBUTES;
                pass.checkAttributes(element, "element", carried);
                out.append("    <element identifier=\"").append(attr(pass.identifier(id, "element")))
                        .append("\" xsi:type=\"").append(type.get()).append("\">\n");
                langString(out, 3, "name", element.attribute("name").orElse(""));
                conceptBody(out, element, "element");
                out.append("    </element>\n");
            }
            return section("elements", out);
        }

        private StringBuilder relationships() {
            Map<String, DocumentNode> candidates = new LinkedHashMap<>();
            for (DocumentNode relationship : document.relationships()) {
                String id = relationship.archiId().value();
                if (relationshipType(relationship).isEmpty()) {
                    pass.loss(id, "relationship", "type", Message.of(InterchangeMessages.OEF_LOSS_CONCEPT_TYPE,
                            relationship.archiType().orElse("")));
                } else {
                    candidates.put(id, relationship);
                }
            }
            Set<String> kept = endpointsClosure(candidates, pass.concepts());
            StringBuilder out = new StringBuilder();
            for (DocumentNode relationship : candidates.values()) {
                String id = relationship.archiId().value();
                if (!kept.contains(id)) {
                    pass.loss(id, "relationship", "endpoint", Message.of(InterchangeMessages.OEF_LOSS_ENDPOINT));
                    continue;
                }
                pass.concepts().add(id);
                String type = relationshipType(relationship).orElseThrow();
                Set<String> carried = union(ELEMENT_ATTRIBUTES, "source", "target");
                StringBuilder extra = new StringBuilder();
                switch (type) {
                    case "Access" -> {
                        carried = union(carried, "accessType");
                        Optional<String> value = relationship.attribute("accessType");
                        Optional<AccessType> access = value.isEmpty() ? Optional.of(AccessType.WRITE)
                                : AccessType.fromFileValue(value.get());
                        if (access.isPresent()) {
                            extra.append(" accessType=\"").append(ACCESS.get(access.get())).append('"');
                        } else {
                            pass.attributeLoss(id, "relationship", new Attribute("accessType", value.get()));
                        }
                    }
                    case "Association" -> {
                        carried = union(carried, "directed");
                        if (relationship.attribute("directed").filter("true"::equals).isPresent()) {
                            extra.append(" isDirected=\"true\"");
                        }
                    }
                    case "Influence" -> {
                        carried = union(carried, "strength");
                        relationship.attribute("strength").filter(s -> !s.isEmpty())
                                .ifPresent(s -> extra.append(" modifier=\"").append(attr(s)).append('"'));
                    }
                    default -> {
                    }
                }
                pass.checkAttributes(relationship, "relationship", carried);
                out.append("    <relationship identifier=\"").append(attr(pass.identifier(id, "relationship")))
                        .append("\" xsi:type=\"").append(type).append("\" source=\"")
                        .append(attr(ref(relationship.attribute("source").orElse("")))).append("\" target=\"")
                        .append(attr(ref(relationship.attribute("target").orElse("")))).append('"').append(extra);
                StringBuilder body = new StringBuilder();
                relationship.attribute("name").filter(n -> !n.isEmpty()).ifPresent(n -> langString(body, 3, "name", n));
                conceptBody(body, relationship, "relationship");
                close(out, body, 2, "relationship");
            }
            return section("relationships", out);
        }

        /** Документация и свойства объекта модели; прочее содержимое — в отчёт. */
        private void conceptBody(StringBuilder out, DocumentNode node, String kind) {
            pass.documentation(out, 3, node);
            pass.checkContent(node, kind, Set.of("documentation", "property"));
            pass.properties(out, 3, node.values("property"));
        }

        private Optional<String> elementType(DocumentNode element) {
            String xsiType = element.archiType().orElse("");
            if (xsiType.equals(JUNCTION)) {
                return Optional.of(element.attribute("type").filter("or"::equals).isPresent() ? "OrJunction"
                        : "AndJunction");
            }
            return xsiType.startsWith(PREFIX) ? Optional.of(xsiType.substring(PREFIX.length()))
                    .filter(ELEMENT_TYPES::contains) : Optional.empty();
        }

        private Optional<String> relationshipType(DocumentNode relationship) {
            String xsiType = relationship.archiType().orElse("");
            if (!xsiType.startsWith(PREFIX) || !xsiType.endsWith(RELATIONSHIP_SUFFIX)) {
                return Optional.empty();
            }
            return Optional.of(xsiType.substring(PREFIX.length(),
                            xsiType.length() - RELATIONSHIP_SUFFIX.length()))
                    .filter(RELATIONSHIP_TYPES::contains);
        }

        // ── папки ────────────────────────────────────────────────────────

        private StringBuilder organizations() {
            StringBuilder out = new StringBuilder();
            for (DocumentNode folder : document.folders()) {
                folder(out, folder, 2, true);
            }
            if (out.isEmpty()) {
                return out;
            }
            return new StringBuilder("  <organizations>\n").append(out).append("  </organizations>\n");
        }

        private void folder(StringBuilder out, DocumentNode folder, int depth, boolean top) {
            String id = folder.archiId().value();
            Set<String> carried = FOLDER_ATTRIBUTES;
            if (top) {
                carried = union(carried, "type");
                folder.attribute("type").ifPresent(type -> pass.loss(id, "folder", "type",
                        Message.of(InterchangeMessages.OEF_LOSS_FOLDER_TYPE, type)));
            }
            pass.checkAttributes(folder, "folder", carried);
            pass.checkContent(folder, "folder", Set.of("documentation"));
            indent(out, depth).append("<item identifier=\"").append(attr(pass.identifier(id, "folder")))
                    .append("\">\n");
            folder.attribute("name").filter(n -> !n.isEmpty()).ifPresent(n -> langString(out, depth + 1, "label", n));
            pass.documentation(out, depth + 1, folder);
            for (DocumentNode child : folder.children()) {
                String childId = child.archiId().value();
                if (child.tag().equals("folder")) {
                    folder(out, child, depth + 1, false);
                } else if (pass.concepts().contains(childId) || pass.diagrams().contains(childId)) {
                    indent(out, depth + 1).append("<item identifierRef=\"").append(attr(ref(childId))).append("\"/>\n");
                }
            }
            indent(out, depth).append("</item>\n");
        }
    }
}
