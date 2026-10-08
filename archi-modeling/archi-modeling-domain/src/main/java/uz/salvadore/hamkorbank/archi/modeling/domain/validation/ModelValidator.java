package uz.salvadore.hamkorbank.archi.modeling.domain.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationMatrix;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationViolation;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationshipType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;

/**
 * Отчёт валидации метамодели без ИИ ({@code GET /models/{id}/validate}). Здесь видны
 * нарушения, которые импорт принял, а не отклонил (FR-10, INV-MDL-007): рисовать такое
 * заново нельзя, но существующая модель обязана открываться.
 */
public final class ModelValidator {

    public static final String OPAQUE_CODE = "MDL_OPAQUE_OBJECT";

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
                    "Связь " + v.relationship() + " от " + v.source().simpleName() + " к " + v.target().simpleName()
                            + " не разрешена в ArchiMate 3.2",
                    "RELATIONSHIP", relationship.archiId().value(), suggestion(v))));
        }
        for (Element element : model.elements().values()) {
            if (!element.supported()) {
                findings.add(new ValidationFinding(Severity.INFO, OPAQUE_CODE,
                        "Тип " + element.archiType().simpleName() + " хранится как есть и не редактируется "
                                + "в текущей фазе метамодели",
                        "ELEMENT", element.archiId().value(), Optional.empty()));
            }
        }
        for (Relationship relationship : model.relationships().values()) {
            if (!relationship.supported() && registry.find(relationship.archiType()).isEmpty()) {
                findings.add(new ValidationFinding(Severity.INFO, OPAQUE_CODE,
                        "Тип связи " + relationship.archiType().simpleName() + " метамодели неизвестен",
                        "RELATIONSHIP", relationship.archiId().value(), Optional.empty()));
            }
        }
        findings.sort(Comparator.comparing(ValidationFinding::severity).thenComparing(ValidationFinding::targetId));
        return findings;
    }

    private static Optional<String> suggestion(RelationViolation violation) {
        if (violation.permitted().isEmpty()) {
            return Optional.of("Для этой пары типов матрица не допускает ни одной связи");
        }
        return Optional.of("Допустимы: " + violation.permitted().stream().sorted()
                .map(RelationshipType::name).collect(Collectors.joining(", ")));
    }
}
