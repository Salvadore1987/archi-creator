package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Матрица допустимых связей ArchiMate 3.2 (INV-MDL-007, FR-10).
 *
 * <p>Правило действует на <em>создание</em> связи: {@link #requirePermitted}
 * отклоняет недопустимый тип. Импорт ему не подчиняется — {@link #check}
 * только сообщает о нарушении, и решение о нём принимает строгость импорта
 * (INV-IXC-007). Реальные модели содержат исторические нарушения, и продукт,
 * который отказывается их открыть, бесполезен.
 *
 * <p>Таблица — ресурс {@code archimate-3.2-relationships.xml}, перенесённый
 * из Archi без правок: расхождение с редактором Archi означало бы, что связь,
 * нарисованная в одном инструменте, отвергается другим.
 */
public final class RelationMatrix {

    private static final String RESOURCE = "archimate-3.2-relationships.xml";
    /** Так матрица называет связь, выступающую концом другой связи. */
    private static final String RELATIONSHIP_CONCEPT = "Relationship";

    private static final RelationMatrix ARCHIMATE_32 = load();

    private final Map<String, Map<String, Set<RelationshipType>>> permitted;

    private RelationMatrix(Map<String, Map<String, Set<RelationshipType>>> permitted) {
        this.permitted = permitted;
    }

    public static RelationMatrix archimate32() {
        return ARCHIMATE_32;
    }

    /**
     * Допустимые типы связи от {@code source} к {@code target}. Тип, которого
     * матрица не знает, не получает ни одной связи: судить о нём нечем.
     */
    public Set<RelationshipType> permitted(ArchiType source, ArchiType target) {
        return permitted.getOrDefault(concept(source), Map.of()).getOrDefault(concept(target), Set.of());
    }

    public boolean isPermitted(ArchiType source, ArchiType target, RelationshipType relationship) {
        return permitted(source, target).contains(relationship);
    }

    /** Знает ли матрица этот тип как конец связи. */
    public boolean knows(ArchiType type) {
        return permitted.containsKey(concept(type));
    }

    /**
     * Проверка при создании связи: недопустимый тип отклоняется (INV-MDL-007).
     *
     * @throws RelationNotPermittedException с перечнем допустимых для этой пары
     */
    public void requirePermitted(ArchiType source, ArchiType target, RelationshipType relationship) {
        if (!isPermitted(source, target, relationship)) {
            throw new RelationNotPermittedException(source, target, relationship, permitted(source, target));
        }
    }

    /**
     * Проверка импортированной связи: нарушение сообщается, а не отклоняется
     * (FR-10). Связь вне матрицы — неизвестного типа или между неизвестными
     * типами — нарушением не считается: правила для неё нет.
     */
    public Optional<RelationViolation> check(ArchiType source, ArchiType target, ArchiType relationship) {
        Optional<RelationshipType> type = RelationshipType.fromArchiType(relationship);
        if (type.isEmpty() || !knows(source) || !knows(target) || isPermitted(source, target, type.get())) {
            return Optional.empty();
        }
        return Optional.of(new RelationViolation(source, target, type.get(), permitted(source, target)));
    }

    private static String concept(ArchiType type) {
        return RelationshipType.fromArchiType(type).isPresent() ? RELATIONSHIP_CONCEPT : type.simpleName();
    }

    private static RelationMatrix load() {
        try (InputStream in = RelationMatrix.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("нет ресурса матрицы связей: " + RESOURCE);
            }
            return new RelationMatrix(parse(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (XMLStreamException e) {
            throw new IllegalStateException("матрица связей не разбирается: " + RESOURCE, e);
        }
    }

    private static Map<String, Map<String, Set<RelationshipType>>> parse(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XMLStreamReader xml = factory.createXMLStreamReader(in);
        Map<String, Map<String, Set<RelationshipType>>> matrix = new HashMap<>();
        Map<String, Set<RelationshipType>> targets = null;
        while (xml.hasNext()) {
            if (xml.next() != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (xml.getLocalName()) {
                case "source" -> {
                    targets = new HashMap<>();
                    matrix.put(xml.getAttributeValue(null, "concept"), targets);
                }
                case "target" -> {
                    if (targets == null) {
                        throw new IllegalStateException("target вне source в " + RESOURCE);
                    }
                    targets.put(xml.getAttributeValue(null, "concept"),
                            relations(xml.getAttributeValue(null, "relations")));
                }
                default -> { }
            }
        }
        matrix.replaceAll((source, row) -> Collections.unmodifiableMap(row));
        return Collections.unmodifiableMap(matrix);
    }

    private static Set<RelationshipType> relations(String keys) {
        EnumSet<RelationshipType> set = EnumSet.noneOf(RelationshipType.class);
        for (char key : keys.toCharArray()) {
            set.add(RelationshipType.fromKey(Character.toLowerCase(key))
                    .orElseThrow(() -> new IllegalStateException("неизвестная буква связи '" + key + "'")));
        }
        return Collections.unmodifiableSet(set);
    }
}
