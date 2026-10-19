package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.mapping.DocumentDecomposer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.IdempotencyConflictException;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportIntake;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportRequest;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiIdGenerator;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationViolation;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Импорт файла {@code .archimate} новой моделью.
 *
 * <p>Сессия проходит {@code RECEIVED → PARSED → VALIDATED → APPLIED} без пропуска шагов;
 * повреждённый файл отклоняется на разборе в любом режиме, нарушения методологии —
 * только в строгом. Повтор с тем же ключом отдаёт результат первой попытки, принятой
 * или отклонённой.
 *
 * <p>Импорт идёт в три шага: приём читает строгость и ищет попытку с тем же ключом;
 * разбор, проверка методологии и раскладка — без транзакции, файл бывает большим;
 * применение — одна транзакция с итогом сессии, записью модели и её первой версии.
 * Перед применением ключ проверяется ещё раз: повтор, пришедший во время разбора,
 * получает итог первой попытки, а не вторую модель.
 */
public final class ImportService {

    private final ImportSessionRepository sessions;
    private final ImportPolicy policy;
    private final ModelExchangeApplicationService models;
    private final ArchiDocumentReader reader;
    private final UnitOfWork unitOfWork;
    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final TextCatalog texts;
    private final Clock clock;
    private final UuidV7 uuids;
    private final ImportGate gate;
    private final MethodologyCheck methodology = new MethodologyCheck();

    public ImportService(ImportSessionRepository sessions, ImportPolicy policy,
                         ModelExchangeApplicationService models, ArchiDocumentReader reader, UnitOfWork unitOfWork,
                         InterchangeEvents events, InterchangeMetrics metrics, TextCatalog texts, Clock clock,
                         int maxParallelImportsPerWorkspace) {
        this.texts = texts;
        this.sessions = sessions;
        this.policy = policy;
        this.models = models;
        this.reader = reader;
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
        this.uuids = new UuidV7(clock);
        this.gate = new ImportGate(maxParallelImportsPerWorkspace);
    }

    /**
     * Импортирует файл {@code .archimate} новой моделью пространства; повтор с тем же ключом
     * идемпотентности возвращает прежний итог. Ключ не задан ({@code null} или пустой) — свой на каждую попытку.
     */
    public ImportReport importFile(EditorIdentity actor, UUID workspaceId, String fileName, byte[] content,
                                   String idempotencyKey) {
        InterchangeOperation operation = InterchangeOperation.IMPORT_ARCHIMATE_FILE;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            WorkspaceId workspace = new WorkspaceId(workspaceId);
            String key = idempotencyKey == null || idempotencyKey.isBlank() ? uuids.next().toString() : idempotencyKey;
            return gate.through(workspace, () -> {
                boolean strict = unitOfWork.read(() -> policy.strictImport(workspace));
                ImportRequest request = request(workspace, fileName, content, key, strict, actor);
                ImportIntake.Intake intake = unitOfWork.read(() -> intake(request));
                if (intake.replayed()) {
                    return report(intake.session(), true);
                }
                Prepared prepared = prepare(intake.session(), content, actor);
                return unitOfWork.write(() -> apply(request, prepared, actor));
            });
        });
    }

    /** Строгость импорта пространства меняет только {@code ADMIN}. */
    public void configureStrictImport(EditorIdentity actor, UUID workspaceId, boolean strict) {
        InterchangeOperation operation = InterchangeOperation.CONFIGURE_IMPORT_POLICY;
        metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            unitOfWork.write(() -> policy.setStrictImport(new WorkspaceId(workspaceId), strict));
            return null;
        });
    }

    /** Включён ли строгий режим импорта пространства. */
    public boolean strictImport(UUID workspaceId) {
        return unitOfWork.read(() -> policy.strictImport(new WorkspaceId(workspaceId)));
    }

    private ImportIntake.Intake intake(ImportRequest request) {
        try {
            return new ImportIntake(sessions, uuids, clock).receive(request);
        } catch (IdempotencyConflictException conflict) {
            throw new InterchangeException(conflict.code(), Failure.CONFLICT, conflict.reason(),
                    Map.of("importSessionId", conflict.existing().id().toString()));
        }
    }

    /**
     * Разобранная попытка до применения: сессия и, если файл принят, модель для записи.
     *
     * @param model {@code null} — сессия отклонена, записывать нечего
     */
    private record Prepared(ImportSession session, ModelContent model, Instant now) {
    }

    /** Разбор, методология и раскладка — без транзакции. */
    private Prepared prepare(ImportSession session, byte[] content, EditorIdentity actor) {
        session.parse(reader, new ByteArrayInputStream(content), this::findingId, clock.instant());
        if (session.status() == ImportStatus.REJECTED) {
            return new Prepared(session, null, null);
        }
        ModelDocument document = Objects.requireNonNull(session.document());
        session.validate(methodology.check(document, this::findingId), clock.instant());
        if (session.status() == ImportStatus.REJECTED) {
            return new Prepared(session, null, null);
        }
        Instant now = clock.instant();
        ModelId modelId = ModelId.of(uuids.next());
        ArchiIdGenerator archiIds = new ArchiIdGenerator(uuids::next);
        ModelContent model = new DocumentDecomposer(uuids::next, archiIds::next).decompose(document,
                new DocumentDecomposer.Identity(modelId, uz.salvadore.hamkorbank.archi.modeling.domain.workspace
                        .WorkspaceId.of(session.workspaceId().value()), actor.subject(), now, now, 0));
        return new Prepared(session, model, now);
    }

    /** Итог попытки — в транзакции записи; ключ, занятый за время разбора, отдаёт первую попытку. */
    private ImportReport apply(ImportRequest request, Prepared prepared, EditorIdentity actor) {
        ImportIntake.Intake again = intake(request);
        if (again.replayed()) {
            return report(again.session(), true);
        }
        ImportSession session = prepared.session();
        if (prepared.model() == null) {
            sessions.save(session);
            return finished(session);
        }
        ModelContent model = prepared.model();
        ModelId modelId = model.model().header().id();
        VersionResponse version = models.importModel(actor, model,
                texts.text(Message.of(InterchangeMessages.COMMENT_IMPORTED, session.sourceName())));
        int opaque = (int) (model.model().elements().values().stream().filter(e -> !e.supported()).count()
                + model.model().relationships().values().stream().filter(r -> !r.supported()).count());
        session.apply(
                new uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId(modelId.value()),
                version.versionNo(), opaque, prepared.now());
        sessions.save(session);
        events.publish(session.pullEvents());
        metrics.opaqueObjects(modelId.value(), opaque);
        return finished(session);
    }

    private ImportRequest request(WorkspaceId workspace, String fileName, byte[] content, String key,
                                  boolean strict, EditorIdentity actor) {
        try {
            return ImportRequest.of(workspace, null, fileName, content, key, strict, actor.subject());
        } catch (InvalidValueException invalid) {
            throw new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE, invalid.reason(),
                    Map.of());
        } catch (IllegalArgumentException invalid) {
            throw new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE,
                    Message.of(InterchangeMessages.INVALID_REQUEST), Map.of());
        }
    }

    private ImportReport finished(ImportSession session) {
        ImportReport report = report(session, false);
        metrics.importFinished(report.status() == ImportStatus.APPLIED ? "applied"
                : report.rejectedAsCorrupt() ? "rejected_corrupt" : "rejected_strict", session.strictMode(),
                session.findings());
        return report;
    }

    private static ImportReport report(ImportSession session, boolean replayed) {
        boolean corrupt = session.status() == ImportStatus.REJECTED && session.document() == null
                && session.findings().stream().noneMatch(f -> f.code().equals(RelationViolation.CODE));
        return new ImportReport(session.id(), session.status(),
                session.appliedModelId() == null ? null : session.appliedModelId().value(), session.appliedVersionNo(),
                session.findings(), replayed, corrupt);
    }

    private FindingId findingId() {
        return new FindingId(uuids.next());
    }
}
