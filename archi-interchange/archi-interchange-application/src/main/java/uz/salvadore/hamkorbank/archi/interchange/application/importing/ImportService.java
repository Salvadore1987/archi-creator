package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.mapping.DocumentDecomposer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.IdempotencyConflictException;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportApplied;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportIntake;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportRequest;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelImportService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiIdGenerator;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * UC-IXC-001: импорт файла {@code .archimate} новой моделью (FR-01, FR-49, FR-50).
 *
 * <p>Сессия проходит {@code RECEIVED → PARSED → VALIDATED → APPLIED} (INV-IXC-002);
 * повреждённый файл отклоняется на разборе в любом режиме, нарушения методологии —
 * только в строгом (INV-IXC-007). Применение — одна транзакция с записью модели
 * и её первой версии. Повтор с тем же ключом отдаёт результат первой попытки
 * (INV-IXC-003), отклонённой — тоже.
 */
public final class ImportService {

    private final ImportSessionRepository sessions;
    private final ImportPolicy policy;
    private final ModelImportService models;
    private final ArchiDocumentReader reader;
    private final UnitOfWork unitOfWork;
    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final Clock clock;
    private final UuidV7 uuids;
    private final MethodologyCheck methodology = new MethodologyCheck();

    public ImportService(ImportSessionRepository sessions, ImportPolicy policy, ModelImportService models,
                         ArchiDocumentReader reader, UnitOfWork unitOfWork, InterchangeEvents events,
                         InterchangeMetrics metrics, Clock clock) {
        this.sessions = sessions;
        this.policy = policy;
        this.models = models;
        this.reader = reader;
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
        this.uuids = new UuidV7(clock);
    }

    public ImportReport importFile(EditorIdentity actor, UUID workspaceId, String fileName, byte[] content,
                                   Optional<String> idempotencyKey) {
        InterchangeOperation operation = InterchangeOperation.IMPORT_ARCHIMATE_FILE;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            WorkspaceId workspace = new WorkspaceId(workspaceId);
            String key = idempotencyKey.filter(k -> !k.isBlank()).orElseGet(() -> UUID.randomUUID().toString());
            ImportRequest request = request(workspace, fileName, content, key, actor);
            return unitOfWork.write(() -> {
                ImportIntake.Intake intake = intake(request);
                if (intake.replayed()) {
                    return report(intake.session(), true);
                }
                return process(intake.session(), content, actor);
            });
        });
    }

    /** Строгость импорта пространства меняет только {@code ADMIN} (FR-49). */
    public void configureStrictImport(EditorIdentity actor, UUID workspaceId, boolean strict) {
        InterchangeOperation operation = InterchangeOperation.CONFIGURE_IMPORT_POLICY;
        metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            unitOfWork.write(() -> policy.setStrictImport(new WorkspaceId(workspaceId), strict));
            return null;
        });
    }

    public boolean strictImport(UUID workspaceId) {
        return unitOfWork.read(() -> policy.strictImport(new WorkspaceId(workspaceId)));
    }

    private ImportIntake.Intake intake(ImportRequest request) {
        try {
            return new ImportIntake(sessions, uuids, clock).receive(request);
        } catch (IdempotencyConflictException conflict) {
            throw new InterchangeException(IdempotencyConflictException.INVARIANT, Failure.CONFLICT,
                    "ключ идемпотентности уже использован для другого файла",
                    Map.of("importSessionId", conflict.existing().id().toString()));
        }
    }

    private ImportReport process(ImportSession session, byte[] content, EditorIdentity actor) {
        session.parse(reader, new ByteArrayInputStream(content), this::findingId, clock.instant());
        if (session.status() == ImportStatus.REJECTED) {
            sessions.save(session);
            return report(session, false);
        }
        ModelDocument document = session.document().orElseThrow();
        session.validate(methodology.check(document, this::findingId), clock.instant());
        if (session.status() == ImportStatus.REJECTED) {
            sessions.save(session);
            return report(session, false);
        }
        Instant now = clock.instant();
        ModelId modelId = ModelId.of(uuids.next());
        ArchiIdGenerator archiIds = new ArchiIdGenerator();
        ModelContent model = new DocumentDecomposer(uuids::next, archiIds::next).decompose(document,
                new DocumentDecomposer.Identity(modelId, uz.salvadore.hamkorbank.archi.modeling.domain.workspace
                        .WorkspaceId.of(session.workspaceId().value()), actor.subject(), now, now, 0));
        ModelVersion version = models.store(model, actor, "Импорт файла " + session.sourceName());
        int opaque = (int) (model.model().elements().values().stream().filter(e -> !e.supported()).count()
                + model.model().relationships().values().stream().filter(r -> !r.supported()).count());
        ImportApplied applied = session.apply(
                new uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId(modelId.value()),
                version.versionNo(), opaque, now);
        sessions.save(session);
        events.publish(List.of(applied));
        return report(session, false);
    }

    private ImportRequest request(WorkspaceId workspace, String fileName, byte[] content, String key,
                                  EditorIdentity actor) {
        try {
            return ImportRequest.of(workspace, Optional.empty(), fileName, content, key,
                    policy.strictImport(workspace), actor.subject());
        } catch (IllegalArgumentException invalid) {
            throw new InterchangeException("IXC_INVALID_REQUEST", Failure.UNPROCESSABLE, invalid.getMessage(), Map.of());
        }
    }

    private static ImportReport report(ImportSession session, boolean replayed) {
        boolean corrupt = session.status() == ImportStatus.REJECTED && session.document().isEmpty()
                && session.findings().stream().noneMatch(f -> f.code().equals("RELATION_NOT_PERMITTED"));
        return new ImportReport(session.id(), session.status(),
                session.appliedModelId().map(m -> m.value()), session.appliedVersionNo(), session.findings(),
                replayed, corrupt);
    }

    private FindingId findingId() {
        return new FindingId(uuids.next());
    }
}
