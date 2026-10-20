package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ImportArchimateFileCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse.Outcome;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.MethodologyCheck;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.ImportDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.application.mapping.DocumentDecomposer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.IdempotencyConflictException;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportIntake;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportRequest;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.Severity;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ResolveWorkspaceQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.ModelContent;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.WorkspaceApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiIdGenerator;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.RelationViolation;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Импорт файла {@code .archimate} новой моделью.
 *
 * <p>Сессия проходит {@code RECEIVED → PARSED → VALIDATED → APPLIED} без пропуска шагов;
 * повреждённый файл отклоняется на разборе в любом режиме, нарушения методологии —
 * только в строгом. Повтор с тем же ключом отдаёт результат первой попытки, принятой
 * или отклонённой. Исход попытки решается здесь один раз — для ответа и для метрики.
 *
 * <p>Импорт идёт в три шага: приём читает строгость и ищет попытку с тем же ключом;
 * разбор, проверка методологии и раскладка — без транзакции, файл бывает большим;
 * применение — одна транзакция с итогом сессии, записью модели и её первой версии.
 * Перед применением ключ проверяется ещё раз: повтор, пришедший во время разбора,
 * получает итог первой попытки, а не вторую модель.
 */
public final class ArchimateFileImportCommandHandler {

    private final InterchangeKernel kernel;
    private final ImportSessionRepository sessions;
    private final ImportPolicy policy;
    private final ModelExchangeApplicationService models;
    private final WorkspaceApplicationService workspaces;
    private final ArchiDocumentReader reader;
    private final ImportGate gate;
    private final MethodologyCheck methodology = new MethodologyCheck();

    public ArchimateFileImportCommandHandler(InterchangeKernel kernel, ImportSessionRepository sessions,
                                             ImportPolicy policy, ModelExchangeApplicationService models,
                                             WorkspaceApplicationService workspaces, ArchiDocumentReader reader,
                                             int maxParallelImportsPerWorkspace) {
        this.kernel = kernel;
        this.sessions = sessions;
        this.policy = policy;
        this.models = models;
        this.workspaces = workspaces;
        this.reader = reader;
        this.gate = new ImportGate(maxParallelImportsPerWorkspace);
    }

    /** Импорт файла; пространство, имя и ключ, которых клиент не задал, подставляются здесь. */
    public ImportArchimateFileResponse handle(EditorIdentity actor, ImportArchimateFileCommand command) {
        return kernel.run(InterchangeOperation.IMPORT_ARCHIMATE_FILE, actor, () -> {
            WorkspaceId workspace = new WorkspaceId(workspaces.resolveWorkspace(
                    new ResolveWorkspaceQuery(command.workspaceId())).workspaceId());
            String key = command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                    ? kernel.uuids().next().toString() : command.idempotencyKey();
            return gate.through(workspace, () -> {
                boolean strict = kernel.unitOfWork().read(() -> policy.strictImport(workspace));
                ImportRequest request = request(workspace, command, key, strict, actor);
                ImportIntake.Intake intake = kernel.unitOfWork().read(() -> intake(request));
                if (intake.replayed()) {
                    return response(intake.session(), true);
                }
                Prepared prepared = prepare(intake.session(), command.content(), actor);
                try {
                    return kernel.unitOfWork().write(() -> apply(request, prepared, actor));
                } catch (ModelingDomainException refused) {
                    recordRefusal(prepared.session(), refused);
                    throw refused;
                }
            });
        });
    }

    private ImportIntake.Intake intake(ImportRequest request) {
        try {
            return new ImportIntake(sessions, kernel.uuids(), kernel.clock()).receive(request);
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
        session.parse(reader, new ByteArrayInputStream(content), this::findingId, kernel.now());
        if (session.status() == ImportStatus.REJECTED) {
            return new Prepared(session, null, null);
        }
        ModelDocument document = Objects.requireNonNull(session.document());
        session.validate(methodology.check(document, this::findingId), kernel.now());
        if (session.status() == ImportStatus.REJECTED) {
            return new Prepared(session, null, null);
        }
        Instant now = kernel.now();
        ModelId modelId = ModelId.of(kernel.uuids().next());
        ArchiIdGenerator archiIds = new ArchiIdGenerator(kernel.uuids()::next);
        try {
            ModelContent model = new DocumentDecomposer(kernel.uuids()::next, archiIds::next).decompose(document,
                    new DocumentDecomposer.Identity(modelId, uz.salvadore.hamkorbank.archi.modeling.domain.workspace
                            .WorkspaceId.of(session.workspaceId().value()), actor.subject(), now, now, 0));
            return new Prepared(session, model, now);
        } catch (InterchangeDomainException unbuildable) {
            return unbuildable(session, unbuildable.reason());
        } catch (ModelingDomainException unbuildable) {
            return unbuildable(session, unbuildable.reason());
        }
    }

    /** Файл разобран, но модель из него не собирается — тот же отказ, что у повреждённого файла. */
    private Prepared unbuildable(ImportSession session, Object reason) {
        session.reject(List.of(new ImportFinding(findingId(), Severity.ERROR, InterchangeCodes.CORRUPT_DOCUMENT,
                Message.of(InterchangeMessages.IMPORT_CONTENT_REJECTED, reason), null, null)), kernel.now());
        return new Prepared(session, null, null);
    }

    /**
     * Запись модели отказала — транзакция откачена; попытка уходит в журнал отклонённой
     * отдельной транзакцией, чтобы отказ был виден в отчёте и повтор ключа отдавал его же.
     */
    private void recordRefusal(ImportSession session, ModelingDomainException refused) {
        session.reject(List.of(new ImportFinding(findingId(), Severity.ERROR, refused.code(),
                Message.of(InterchangeMessages.IMPORT_APPLY_FAILED, refused.reason()), null, null)), kernel.now());
        kernel.unitOfWork().write(() -> sessions.save(session));
        kernel.metrics().importFinished("rejected_apply", session.strictMode(), session.findings());
    }

    /** Итог попытки — в транзакции записи; ключ, занятый за время разбора, отдаёт первую попытку. */
    private ImportArchimateFileResponse apply(ImportRequest request, Prepared prepared, EditorIdentity actor) {
        ImportIntake.Intake again = intake(request);
        if (again.replayed()) {
            return response(again.session(), true);
        }
        ImportSession session = prepared.session();
        if (prepared.model() == null) {
            sessions.save(session);
            return finished(session);
        }
        ModelContent model = prepared.model();
        ModelId modelId = model.model().header().id();
        VersionResponse version = models.importModel(actor, model,
                kernel.texts().text(Message.of(InterchangeMessages.COMMENT_IMPORTED, session.sourceName())));
        int opaque = model.model().opaqueCount();
        session.apply(
                new uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId(modelId.value()),
                version.versionNo(), opaque, prepared.now());
        sessions.save(session);
        kernel.events().publish(session.pullEvents());
        kernel.metrics().opaqueObjects(modelId.value(), opaque);
        return finished(session);
    }

    private static ImportRequest request(WorkspaceId workspace, ImportArchimateFileCommand command, String key,
                                         boolean strict, EditorIdentity actor) {
        try {
            return ImportRequest.of(workspace, null, ImportRequest.sourceNameOrDefault(command.fileName()),
                    command.content(), key, strict, actor.subject());
        } catch (InvalidValueException invalid) {
            throw new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE, invalid.reason(),
                    Map.of());
        }
    }

    private ImportArchimateFileResponse finished(ImportSession session) {
        ImportArchimateFileResponse response = response(session, false);
        kernel.metrics().importFinished(switch (response.outcome()) {
            case APPLIED, REPLAYED -> "applied";
            case REJECTED_CORRUPT -> "rejected_corrupt";
            case REJECTED_STRICT -> "rejected_strict";
        }, session.strictMode(), session.findings());
        return response;
    }

    private ImportArchimateFileResponse response(ImportSession session, boolean replayed) {
        Outcome outcome = outcome(session, replayed);
        String refusalCode = switch (outcome) {
            case APPLIED, REPLAYED -> null;
            case REJECTED_CORRUPT -> InterchangeCodes.CORRUPT_DOCUMENT;
            case REJECTED_STRICT -> InterchangeCodes.STRICT_IMPORT_REJECTED;
        };
        String refusalText = switch (outcome) {
            case APPLIED, REPLAYED -> null;
            case REJECTED_CORRUPT -> kernel.texts().text(Message.of(InterchangeMessages.REJECTED_CORRUPT));
            case REJECTED_STRICT -> kernel.texts().text(Message.of(InterchangeMessages.REJECTED_STRICT));
        };
        UUID modelId = session.appliedModelId() == null ? null : session.appliedModelId().value();
        return new ImportArchimateFileResponse(session.id().value(), session.status().name(), outcome, refusalCode,
                refusalText, modelId, modelId == null ? null : session.appliedVersionNo(), replayed,
                ImportDataMapper.findings(session.findings(), kernel.texts()));
    }

    /**
     * Исход попытки. Отклонена строгостью — если среди ошибок только нарушения матрицы связей;
     * любая другая ошибка значит, что повреждён сам файл или модель из него не собирается.
     */
    private static Outcome outcome(ImportSession session, boolean replayed) {
        if (session.status() == ImportStatus.APPLIED) {
            return replayed ? Outcome.REPLAYED : Outcome.APPLIED;
        }
        boolean corrupt = session.findings().stream()
                .anyMatch(f -> f.severity() == Severity.ERROR && !f.code().equals(RelationViolation.CODE));
        return corrupt ? Outcome.REJECTED_CORRUPT : Outcome.REJECTED_STRICT;
    }

    private FindingId findingId() {
        return new FindingId(kernel.uuids().next());
    }
}
