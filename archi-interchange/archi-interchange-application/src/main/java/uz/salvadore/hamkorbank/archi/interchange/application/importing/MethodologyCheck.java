package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.Severity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationMatrix;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationViolation;

/**
 * Проверка методологии на импорте: нарушения матрицы ArchiMate 3.2 —
 * {@code ERROR} с кодом {@code RELATION_NOT_PERMITTED}, неизвестные типы — {@code INFO}.
 * Уровень {@code ERROR} сам по себе не отказ: отклоняет только строгий режим.
 *
 * <p>Здесь соединяются сессия interchange и матрица modeling — доменные модули друг
 * друга не видят, а application-слой видит оба.
 */
public final class MethodologyCheck {

    /** Код находки о типе, которого нет в метамодели. */
    public static final String UNKNOWN_TYPE = InterchangeCodes.UNKNOWN_ELEMENT_TYPE;

    private final RelationMatrix matrix = RelationMatrix.archimate32();
    private final ArchiTypeRegistry registry = ArchiTypeRegistry.archimate32();

    /** Находки методологии по документу: недопустимые связи и неизвестные типы. */
    public List<ImportFinding> check(ModelDocument document, Supplier<FindingId> ids) {
        Map<String, String> types = new HashMap<>();
        document.allNodes().forEach(n -> n.archiType().ifPresent(t -> types.put(n.archiId().value(), t)));
        List<ImportFinding> findings = new ArrayList<>();
        for (DocumentNode relationship : document.relationships()) {
            Optional<ArchiType> type = archiType(relationship.archiType().orElse(""));
            Optional<ArchiType> source = relationship.attribute("source").map(types::get).flatMap(this::archiType);
            Optional<ArchiType> target = relationship.attribute("target").map(types::get).flatMap(this::archiType);
            if (type.isEmpty() || source.isEmpty() || target.isEmpty()) {
                continue;
            }
            matrix.check(source.get(), target.get(), type.get()).ifPresent(v -> findings.add(new ImportFinding(
                    ids.get(), Severity.ERROR, RelationViolation.CODE, message(v),
                    relationship.archiId(), null)));
        }
        for (DocumentNode element : document.elements()) {
            String xsiType = element.archiType().orElse("");
            if (registry.findByXsiType(xsiType).isEmpty()) {
                findings.add(new ImportFinding(ids.get(), Severity.INFO, UNKNOWN_TYPE,
                        Message.of(InterchangeMessages.UNKNOWN_TYPE_STORED, xsiType),
                        element.archiId(), null));
            }
        }
        return findings;
    }

    private Optional<ArchiType> archiType(String value) {
        try {
            return Optional.of(ArchiType.of(value));
        } catch (InvalidValueException notArchimate) {
            return Optional.empty();
        }
    }

    private static Message message(RelationViolation violation) {
        return Message.of(InterchangeMessages.RELATION_NOT_PERMITTED, violation.relationship(),
                violation.source().simpleName(), violation.target().simpleName(), violation.permitted());
    }
}
