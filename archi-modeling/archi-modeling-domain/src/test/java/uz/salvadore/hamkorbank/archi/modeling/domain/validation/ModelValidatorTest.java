package uz.salvadore.hamkorbank.archi.modeling.domain.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.NOW;
import static uz.salvadore.hamkorbank.archi.modeling.domain.model.Models.UUIDS;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Element;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderTreeInitializer;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelFolder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelStatus;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Models;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.Relationship;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

class ModelValidatorTest {

    @Test
    @DisplayName("FR-10: импортированное нарушение матрицы попадает в отчёт, а не в отказ")
    void importedViolationIsReported() {
        FolderId app = FolderId.next(UUIDS);
        FolderId business = FolderId.next(UUIDS);
        FolderId relations = FolderId.next(UUIDS);
        List<ModelFolder> roots = List.of(
                FolderTreeInitializer.root(FolderType.APPLICATION, app, Models.archiId(), SortOrder.ofPosition(0)),
                FolderTreeInitializer.root(FolderType.BUSINESS, business, Models.archiId(), SortOrder.ofPosition(1)),
                FolderTreeInitializer.root(FolderType.RELATIONS, relations, Models.archiId(), SortOrder.ofPosition(2)));
        Element component = new Element(ElementId.next(UUIDS), app, Models.archiId(),
                ArchiType.ofSimpleName("ApplicationComponent"), "АБС", Optional.empty(), List.of(),
                SortOrder.ofPosition(0), true, Optional.empty());
        Element actor = new Element(ElementId.next(UUIDS), business, Models.archiId(),
                ArchiType.ofSimpleName("BusinessActor"), "Клиент", Optional.empty(), List.of(),
                SortOrder.ofPosition(0), true, Optional.empty());
        Relationship forbidden = new Relationship(RelationshipId.next(UUIDS), relations, Models.archiId(),
                ArchiType.ofSimpleName("AssignmentRelationship"), component.id(), actor.id(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(), SortOrder.ofPosition(0), true,
                Optional.empty());
        ModelHeader header = new ModelHeader(ModelId.next(UUIDS), WorkspaceId.next(UUIDS), Models.archiId(), "Файл",
                Optional.empty(), "5.0.0", ModelStatus.ACTIVE, List.of(), Optional.empty(), "a", NOW, NOW, 0);

        ArchitectureModel model = ArchitectureModel.imported(header, roots, List.of(component, actor),
                List.of(forbidden), List.of(), () -> FolderId.next(UUIDS), Models::archiId);
        List<ValidationFinding> findings = ModelValidator.archimate32().validate(model);

        assertEquals(1, findings.size());
        assertEquals("RELATION_NOT_PERMITTED", findings.getFirst().code());
        assertEquals(Severity.ERROR, findings.getFirst().severity());
        assertEquals(forbidden.archiId().value(), findings.getFirst().targetId());
    }
}
