package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryBindings;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryMerges;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemorySessions;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryStates;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.MemoryVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitFakes.RollbackUnitOfWork;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSessionAppliedEvent;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Слияние предложения на фейках: общий предок — коммит последней опубликованной версии,
 * «наше» — содержимое модели, «их» — ветка предложения. Правила слияния — в
 * {@code EntityMergeTest}, JGit и база — в {@code MergeRequestIT}.
 */
class GitMergeTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();
    private static final EditorIdentity ARCHITECT = EditorIdentity.of("architect-merge", Role.ARCHITECT);
    private static final EditorIdentity VIEWER = EditorIdentity.of("viewer-merge", Role.VIEWER);
    private static final String NODE = "<element xsi:type=\"archimate:Node\" name=\"Сервер\" id=\"id-node\"/>";
    private static final String VIEW = """
            <element xsi:type="archimate:ArchimateDiagramModel" name="Контекст" id="id-view">
              <child xsi:type="archimate:Group" id="id-group" name="Ядро">
                <bounds x="100" y="50" width="400" height="200"/>%s
              </child>
            </element>""";
    private static final String NODE_ON_VIEW = """
            <child xsi:type="archimate:DiagramObject" id="id-dnode" archimateElement="id-node">
              <bounds x="10" y="20" width="120" height="55"/>
            </child>""";

    private final WorkspaceId workspace = new WorkspaceId(IDS.generate());
    private final ModelId model = new ModelId(IDS.generate());
    private final MemoryStates modelStates = new MemoryStates(workspace);
    private final MemorySessions sessions = new MemorySessions();
    private final MemoryOutbox outbox = new MemoryOutbox();
    private final MemoryBindings bindings = new MemoryBindings();
    private final MemoryVersions versions = new MemoryVersions();
    private final MemoryMerges merges = new MemoryMerges();
    private final MemoryRepository repository = new MemoryRepository(model);
    private final List<Object> events = new ArrayList<>();
    private GitMergeService service;
    private GitSha published;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
        UnitOfWork transactions = new RollbackUnitOfWork(List.of(modelStates, sessions, outbox, bindings, versions,
                merges));
        ImportPolicy policy = new ImportPolicy() {
            @Override
            public boolean strictImport(WorkspaceId workspaceId) {
                return false;
            }

            @Override
            public void setStrictImport(WorkspaceId workspaceId, boolean value) {
            }
        };
        service = new GitMergeService(bindings, repository, ref -> Optional.empty(), modelStates, sessions, policy,
                outbox, versions, merges, transactions, events::addAll, InterchangeMetrics.NONE,
                message -> message.key() + message.args(), clock);
        bindings.save(GitBinding.configure(workspace, "file:///repo.git", null,
                SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of()));
        published = repository.commit("main", model("АБС", "", ""));
        versions.published = Optional.of(new PublishedVersions.Published(3, published));
        modelStates.state = new MemoryStates.State(xml(model("АБС «Ядро»", "", "")), 4, true);
        outbox.enqueue(workspace, model, 4, Instant.EPOCH);
        repository.branch("proposal", published);
    }

    @Test
    @DisplayName("INV-IXC-010: непересекающееся предложение — без конфликтов, отметки и узлы для холста")
    void disjointProposalIsReportedWithMarks() {
        GitSha proposal = repository.commit("proposal", model("АБС", NODE, VIEW.formatted(NODE_ON_VIEW)));

        GitMergeService.Preview preview = service.preview(ARCHITECT, model, "proposal", Map.of());

        assertEquals(proposal, preview.ref().sha());
        assertEquals(published, preview.mergeBase());
        assertEquals(List.of(), preview.conflicts());
        assertTrue(preview.applicable());
        assertTrue(preview.marks().contains(new GitMergeService.Mark(ArchiId.of("id-node"), ModelChanges.Type.ADDED)));
        GitMergeService.AddedNode added = preview.views().getFirst().nodes().stream()
                .filter(n -> n.archiId().equals(ArchiId.of("id-dnode"))).findFirst().orElseThrow();
        assertEquals(List.of(110, 70, 120, 55), List.of(added.x(), added.y(), added.width(), added.height()),
                "геометрия абсолютна: смещение группы прибавлено");
        assertEquals("Node", added.type());
        assertEquals("Сервер", added.name());
        assertTrue(sessions.rows.isEmpty());
    }

    @Test
    @DisplayName("INV-IXC-010: применение — новая версия с правками обеих сторон, слитый коммит за моделью")
    void disjointProposalIsMerged() {
        GitSha proposal = repository.commit("proposal", model("АБС", NODE, ""));
        GitMergeService.Preview preview = service.preview(ARCHITECT, model, "proposal", Map.of());

        GitMergeService.Merged merged = service.apply(ARCHITECT, model, confirm(preview, Map.of()));

        assertEquals(5, merged.versionNo());
        assertTrue(merged.created());
        assertArrayEquals(xml(model("АБС «Ядро»", NODE, "")), modelStates.state.xml());
        assertEquals("interchange.git.merge-comment[proposal, " + proposal.value().substring(0, 7) + "]",
                modelStates.comments.get(5L));
        assertEquals(Map.of(model, proposal), merges.rows);
        assertEquals(ImportStatus.APPLIED, sessions.rows.get(merged.session()).status());
        assertTrue(events.getFirst() instanceof ImportSessionAppliedEvent applied && applied.versionNo() == 5);
        assertEquals(List.of("PENDING"), outbox.statuses(), "без расхождения очередь не трогается");
    }

    @Test
    @DisplayName("INV-IXC-010: одно поле в обеих ветвях — без выбора не применяется, с выбором — по полю")
    void conflictIsResolvedPerField() {
        repository.commit("proposal", model("АБС theirs", NODE, ""));
        GitMergeService.Preview preview = service.preview(ARCHITECT, model, "proposal", Map.of());

        EntityMerge.Conflict conflict = preview.conflicts().getFirst();
        assertEquals("id-app:name", conflict.id());
        assertFalse(preview.applicable());
        InterchangeException unresolved = assertThrows(InterchangeException.class,
                () -> service.apply(ARCHITECT, model, confirm(preview, Map.of())));
        assertEquals(InterchangeCodes.MERGE_CONFLICTS_UNRESOLVED, unresolved.code());
        assertEquals(Failure.UNPROCESSABLE, unresolved.failure());
        assertEquals(List.of("id-app:name"), unresolved.details().get("conflicts"));
        assertEquals(4, modelStates.state.lastVersionNo());

        GitMergeService.Preview chosen = service.preview(ARCHITECT, model, "proposal",
                Map.of(conflict.id(), "THEIRS"));
        assertTrue(chosen.applicable());
        service.apply(ARCHITECT, model, confirm(chosen, Map.of(conflict.id(), "THEIRS")));

        assertArrayEquals(xml(model("АБС theirs", NODE, "")), modelStates.state.xml());
    }

    @Test
    @DisplayName("INV-IXC-012: модель изменилась после отчёта — 409, сессия REJECTED")
    void staleReportIsRejected() {
        repository.commit("proposal", model("АБС", NODE, ""));
        GitMergeService.Preview preview = service.preview(ARCHITECT, model, "proposal", Map.of());
        modelStates.state = new MemoryStates.State(xml(model("АБС-2", "", "")), 4, true);

        InterchangeException e = assertThrows(InterchangeException.class,
                () -> service.apply(ARCHITECT, model, confirm(preview, Map.of())));

        assertEquals(InterchangeCodes.GIT_IMPORT, e.code());
        assertEquals(ImportStatus.REJECTED, sessions.rows.values().iterator().next().status());
        assertTrue(merges.rows.isEmpty());
    }

    @Test
    @DisplayName("UC-IXC-007: выбор по несуществующему конфликту и чужое значение выбора — 422")
    void invalidResolutionsAreRejected() {
        repository.commit("proposal", model("АБС", NODE, ""));

        assertEquals(InterchangeCodes.INVALID_REQUEST, code(() -> service.preview(ARCHITECT, model,
                "proposal", Map.of("id-app:name", "THEIRS"))));
        assertEquals(InterchangeCodes.INVALID_REQUEST, code(() -> service.preview(ARCHITECT, model,
                "proposal", Map.of("id-app:name", "MINE"))));
    }

    @Test
    @DisplayName("UC-IXC-007: нет опубликованной версии, нет каталога в предке, нет роли — свои отказы")
    void refusals() {
        repository.commit("proposal", model("АБС", NODE, ""));
        assertEquals(InterchangeCodes.ACCESS_DENIED, code(() -> service.preview(VIEWER, model,
                "proposal", Map.of())));
        assertEquals(InterchangeCodes.GIT_REF_NOT_FOUND, code(() -> service.preview(ARCHITECT, model,
                "nope", Map.of())));

        GitSha empty = repository.commitFiles("orphan", Map.of());
        repository.commit("orphan", model("АБС", NODE, ""));
        versions.published = Optional.of(new PublishedVersions.Published(3, empty));
        assertEquals(InterchangeCodes.GIT_NO_MERGE_BASE, code(() -> service.preview(ARCHITECT, model,
                "orphan", Map.of())));

        versions.published = Optional.empty();
        assertEquals(InterchangeCodes.GIT_NO_MERGE_BASE, code(() -> service.preview(ARCHITECT, model,
                "proposal", Map.of())));
    }

    @Test
    @DisplayName("FR-37: слияние разошедшейся головы ветки закрывает старые записи очереди")
    void mergeOfDivergedHeadSupersedesQueue() {
        GitSha head = repository.commit("main", model("АБС", NODE, ""));
        outbox.divergeHead(model);
        GitMergeService.Preview preview = service.preview(ARCHITECT, model, "main", Map.of());

        GitMergeService.Merged merged = service.apply(ARCHITECT, model, confirm(preview, Map.of()));

        assertEquals(5, merged.versionNo());
        assertEquals(List.of("SKIPPED"), outbox.statuses());
        assertEquals(head, bindings.find(workspace).orElseThrow().lastCommitSha());
        assertEquals(Map.of(model, head), merges.rows);
    }

    @Test
    @DisplayName("UC-IXC-007: выбрано всё «наше» — версии нет, но слитый коммит записан")
    void allOursKeepsVersionButRecordsMerge() {
        GitSha proposal = repository.commit("proposal", model("АБС theirs", "", ""));
        Map<String, String> ours = Map.of("id-app:name", "OURS");
        GitMergeService.Preview preview = service.preview(ARCHITECT, model, "proposal", ours);

        GitMergeService.Merged merged = service.apply(ARCHITECT, model, confirm(preview, ours));

        assertTrue(preview.unchanged());
        assertFalse(merged.created());
        assertEquals(4, merged.versionNo());
        assertEquals(Map.of(model, proposal), merges.rows);
    }

    private GitMergeService.MergeRequest confirm(GitMergeService.Preview preview, Map<String, String> resolutions) {
        return new GitMergeService.MergeRequest(preview.ref().ref().value(),
                preview.ref().sha().value(), preview.mergeBase().value(),
                preview.baseHash(), resolutions, null);
    }

    private static ModelDocument model(String appName, String tech, String views) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Ландшафт" id="id-model" version="5.0.0">
                  <folder name="Приложения" id="id-apps" type="application">
                    <element xsi:type="archimate:ApplicationComponent" name="%s" id="id-app"/>
                  </folder>
                  <folder name="Технологии" id="id-tech" type="technology">%s</folder>
                  <folder name="Представления" id="id-views" type="diagrams">%s</folder>
                </archimate:model>
                """.formatted(appName, tech, views);
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] xml(ModelDocument document) {
        return new ArchiXmlWriter().write(document);
    }

    private static String code(Runnable call) {
        return assertThrows(InterchangeException.class, call::run).code();
    }
}
