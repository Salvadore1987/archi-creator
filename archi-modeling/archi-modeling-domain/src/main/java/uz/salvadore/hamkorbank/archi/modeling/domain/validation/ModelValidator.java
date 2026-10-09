package uz.salvadore.hamkorbank.archi.modeling.domain.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationMatrix;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationViolation;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;

/**
 * Отчёт валидации метамодели без ИИ ({@code GET /models/{id}/validate}). Здесь видны
 * нарушения, которые импорт принял, а не отклонил: рисовать такое
 * заново нельзя, но существующая модель обязана открываться.
 */
public final class ModelValidator {

    public static final String OPAQUE_CODE = ModelingCodes.OPAQUE_OBJECT;

    private final RelationMatrix matrix;
    private final ArchiTypeRegistry registry;

    public ModelValidator(RelationMatrix matrix, ArchiTypeRegistry registry) {
        this.matrix = matrix;
        this.registry = registry;
    }

    public static ModelValidator archimate32() {
        return new ModelValidator(RelationMatrix.archimate32(), ArchiTypeRegistry.archimate32());
    }

    public List<ValidationFinding> validate(ArchitectureModel model) {
        List<ValidationFinding> findings = new ArrayList<>();
        for (Relationship relationship : model.relationships().values()) {
            Optional<RelationViolation> violation = matrix.check(model.conceptType(relationship.source()),
                    model.conceptType(relationship.target()), relationship.archiType());
            violation.ifPresent(v -> findings.add(new ValidationFinding(Severity.ERROR, RelationViolation.CODE,
                    Message.of(ModelingMessages.VALIDATION_RELATION_NOT_PERMITTED, v.relationship(),
                            v.source().simpleName(), v.target().simpleName()),
                    "RELATIONSHIP", relationship.archiId().value(), suggestion(v))));
        }
        for (Element element : model.elements().values()) {
            if (!element.supported()) {
                findings.add(new ValidationFinding(Severity.INFO, OPAQUE_CODE,
                        Message.of(ModelingMessages.VALIDATION_OPAQUE_ELEMENT, element.archiType().simpleName()),
                        "ELEMENT", element.archiId().value(), Optional.empty()));
            }
        }
        for (Relationship relationship : model.relationships().values()) {
            if (!relationship.supported() && registry.find(relationship.archiType()).isEmpty()) {
                findings.add(new ValidationFinding(Severity.INFO, OPAQUE_CODE,
                        Message.of(ModelingMessages.VALIDATION_UNKNOWN_RELATIONSHIP,
                                relationship.archiType().simpleName()),
                        "RELATIONSHIP", relationship.archiId().value(), Optional.empty()));
            }
        }
        findings.sort(Comparator.comparing(ValidationFinding::severity).thenComparing(ValidationFinding::targetId));
        return findings;
    }

    private static Optional<Message> suggestion(RelationViolation violation) {
        if (violation.permitted().isEmpty()) {
            return Optional.of(Message.of(ModelingMessages.VALIDATION_NONE_PERMITTED));
        }
        return Optional.of(Message.of(ModelingMessages.VALIDATION_PERMITTED, violation.permitted().stream().sorted()
                .map(RelationshipType::name).collect(Collectors.joining(", "))));
    }
}
