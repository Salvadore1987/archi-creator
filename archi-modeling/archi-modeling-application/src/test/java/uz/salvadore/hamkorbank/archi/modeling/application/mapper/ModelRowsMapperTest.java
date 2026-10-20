package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.BendpointValue;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.ModelRows;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.StyleValue;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiIdGenerator;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.RelationshipId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

class ModelRowsMapperTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private final UUID business = IDS.generate();
    private final UUID relations = IDS.generate();
    private final UUID diagrams = IDS.generate();
    private final UUID actor = IDS.generate();
    private final UUID role = IDS.generate();
    private final UUID assignment = IDS.generate();
    private final UUID aggregation = IDS.generate();
    private final UUID view = IDS.generate();
    private final UUID actorNode = IDS.generate();
    private final UUID roleNode = IDS.generate();
    private final UUID edge = IDS.generate();
    private final UUID edgeOnEdge = IDS.generate();

    @Test
    @DisplayName("Строки → агрегаты → строки: значения, ключи и порядок те же")
    void rowsSurviveAggregates() {
        ModelRows rows = rows("5.0.0");

        ModelRows back = ModelRowsMapper.rows(content(rows));

        assertEquals(rows.header(), back.header());
        assertEquals(rows.elements(), back.elements());
        assertEquals(rows.relationships(), back.relationships());
        assertEquals(rows.views(), back.views());
        assertEquals(rows.folders(), back.folders().subList(0, rows.folders().size()),
                "свои папки — в начале и как были; недостающие корни модель дописала после");
    }

    @Test
    @DisplayName("Конец связи и ребра — по ключам строк: связь на связь и ребро на ребро распознаются")
    void endsResolveByRowKeys() {
        ModelContent content = content(rows("5.0.0"));

        assertTrue(content.model().relationships().values().stream()
                .anyMatch(r -> r.target() instanceof RelationshipId), "агрегация на связь");
        assertTrue(content.views().getFirst().edges().values().stream()
                .anyMatch(e -> e.target() instanceof ViewEdgeId), "ребро на ребро");
    }

    @Test
    @DisplayName("Версию Archi, которой modeling не принимает, заменяет его версия по умолчанию")
    void invalidArchiVersionFallsBackToDefault() {
        assertEquals(ModelHeader.DEFAULT_ARCHI_VERSION,
                content(rows("not a version")).model().header().archiVersion());
        assertEquals(ModelHeader.DEFAULT_ARCHI_VERSION, content(rows(null)).model().header().archiVersion());
    }

    @Test
    @DisplayName("Поддержан ли тип, решает каталог при сборке: строка этого не несёт")
    void supportedComesFromCatalog() {
        ModelContent content = content(rows("5.0.0"));

        assertTrue(content.model().elements().values().stream().allMatch(e -> e.supported()));
        assertFalse(content.model().elements().values().isEmpty());
    }

    private ModelContent content(ModelRows rows) {
        ArchiIdGenerator archiIds = new ArchiIdGenerator(IDS::generate);
        return ModelRowsMapper.content(rows, new ModelRowsMapper.Identity(ModelId.of(IDS.generate()),
                WorkspaceId.of(IDS.generate()), "architect", NOW, NOW, 0),
                () -> FolderId.of(IDS.generate()), archiIds::next);
    }

    private ModelRows rows(String archiVersion) {
        StyleValue noStyle = new StyleValue(null, null, null, null, null);
        return new ModelRows(
                new ModelRows.Header("id-model", "Модель", "описание", archiVersion,
                        List.of(new ModelRows.Property("owner", "ИТ", 1000)), "<residue/>"),
                List.of(new ModelRows.Folder(business, null, "id-fb", "Business", "business", 1000, null),
                        new ModelRows.Folder(relations, null, "id-fr", "Relations", "relations", 2000, null),
                        new ModelRows.Folder(diagrams, null, "id-fd", "Views", "diagrams", 3000, null)),
                List.of(new ModelRows.Element(actor, business, "id-actor", "archimate:BusinessActor", "Клиент",
                                null, List.of(), 1000, null),
                        new ModelRows.Element(role, business, "id-role", "archimate:BusinessRole", "Плательщик",
                                "", List.of(new ModelRows.Property("k", "v", 1000)), 2000, "<r/>")),
                List.of(new ModelRows.Relationship(assignment, relations, "id-assign",
                                "archimate:AssignmentRelationship", actor, role, null, null, null, null, List.of(),
                                1000, null),
                        new ModelRows.Relationship(aggregation, relations, "id-aggr",
                                "archimate:AssociationRelationship", actor, assignment, "к связи", null, null, true,
                                List.of(), 2000, null)),
                List.of(new ModelRows.View(view, diagrams, "id-view", "archimate:ArchimateDiagramModel", "Вид",
                        null, null, List.of(), 1000, null,
                        List.of(new ModelRows.Node(actorNode, null, "id-n1", "archimate:DiagramObject", actor,
                                        new ModelRows.Box(10, 20, 120, 55),
                                        new StyleValue("#ffffff", null, null, null, 2), "подпись", null, 1000, null),
                                new ModelRows.Node(roleNode, null, "id-n2", "archimate:DiagramObject", role,
                                        new ModelRows.Box(200, 20, 120, 55), noStyle, null, null, 2000, null)),
                        List.of(new ModelRows.Edge(edge, "id-e1", "archimate:Connection", assignment, actorNode,
                                        roleNode, List.of(new BendpointValue(1, 2, 3, 4)), noStyle, 3000, null),
                                new ModelRows.Edge(edgeOnEdge, "id-e2", "archimate:Connection", aggregation,
                                        actorNode, edge, List.of(), noStyle, 4000, null)))));
    }
}
