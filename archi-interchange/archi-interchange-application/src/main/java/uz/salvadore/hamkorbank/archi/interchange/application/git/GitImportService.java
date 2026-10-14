package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.MethodologyCheck;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Remote;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Unreachable;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.port.SecretResolver;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.RefKind;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ResolvedRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportApplied;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportRequest;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.Severity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;

/**
 * Импорт состояния модели из Git по ссылке — новой версией модели.
 *
 * <p>Два шага. Отчёт разрешает ссылку, собирает каталог модели из коммита в документ,
 * проверяет его, как файл на импорте, и сравнивает с текущим содержимым базы — ничего
 * не записывая. Применение берёт из отчёта коммит и отпечаток содержимого: читается
 * ровно этот коммит, и только если модель с тех пор не менялась, — применяется то,
 * что архитектор видел.
 *
 * <p>Применение — одна транзакция: замена содержимого, версия, закрытие сессии импорта.
 * Отказ внутри неё откатывает всё, а попытка остаётся в журнале сессией {@code REJECTED},
 * записанной уже после отката. Сеть — до транзакции: коммит неизменен, его достаточно
 * прочитать один раз.
 *
 * <p>Применённый коммит, который в этот момент — голова ветки привязки, разрешает
 * расхождение: старые версии модели в очереди публикации пропускаются, голова становится
 * последним коммитом привязки, а версия без изменений сразу получает её идентификатор.
 */
public final class GitImportService {

    private static final Pattern BASE_HASH = Pattern.compile("^[0-9a-f]{64}$");
    private static final int SHORT_SHA = 7;

    /** Что подтверждает архитектор: ссылка и коммит из отчёта, отпечаток содержимого, комментарий. */
    public record PullRequest(Optional<String> ref, Optional<String> commitSha, Optional<String> baseHash,
                              Optional<String> comment) {
    }

    /**
     * Отчёт до применения.
     *
     * @param baseVersionNo последняя версия модели; у модели без версий пусто
     * @param baseHash      отпечаток текущего содержимого — его применение и подтверждает
     * @param applicable    применение пройдёт разбор и строгий режим пространства
     */
    public record Preview(ModelId model, ResolvedRef ref, Optional<Long> baseVersionNo, String baseHash,
                          String targetHash, ModelChanges changes, List<ImportFinding> findings, boolean applicable) {

        public Preview {
            findings = List.copyOf(findings);
        }

        public boolean unchanged() {
            return baseHash.equals(targetHash);
        }
    }

    /** @param created ложь — содержимое совпало с текущим, версия прежняя */
    public record Pulled(ImportSessionId session, ModelId model, long versionNo, boolean created, GitSha commit,
                         List<ImportFinding> findings) {

        public Pulled {
            findings = List.copyOf(findings);
        }
    }

    /** Прочитанный коммит: сессия после разбора и проверки, документ и снимок, если собрались. */
    private record Candidate(ResolvedRef ref, ImportSession session, Optional<ModelDocument> document,
                             Optional<byte[]> xml) {
    }

    private final GitBindingRepository bindings;
    private final GitRepositoryPort repository;
    private final GitRemotes remotes;
    private final ModelStates states;
    private final ImportSessionRepository sessions;
    private final ImportPolicy policy;
    private final GitOutbox outbox;
    private final PublishedVersions versions;
    private final UnitOfWork unitOfWork;
    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final TextCatalog texts;
    private final Clock clock;
    private final UuidV7 uuids;
    private final GitYamlReader reader = new GitYamlReader();
    private final MethodologyCheck methodology = new MethodologyCheck();

    public GitImportService(GitBindingRepository bindings, GitRepositoryPort repository, SecretResolver secrets,
                            ModelStates states, ImportSessionRepository sessions, ImportPolicy policy,
                            GitOutbox outbox, PublishedVersions versions, UnitOfWork unitOfWork,
                            InterchangeEvents events, InterchangeMetrics metrics, TextCatalog texts, Clock clock) {
        this.bindings = bindings;
        this.repository = repository;
        this.remotes = new GitRemotes(secrets, texts);
        this.states = states;
        this.sessions = sessions;
        this.policy = policy;
        this.outbox = outbox;
        this.versions = versions;
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.metrics = metrics;
        this.texts = texts;
        this.clock = clock;
        this.uuids = new UuidV7(clock);
    }

    /** Отчёт об изменениях по ссылке. В базе ничего не меняется, сессия не создаётся. */
    public Preview preview(EditorIdentity actor, ModelId model, Optional<String> ref) {
        InterchangeOperation operation = InterchangeOperation.PULL_MODEL_FROM_GIT;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            GitRef requested = parse(() -> GitRef.of(required(ref, "ref")));
            ModelStates.Current current = states.current(actor, model);
            GitBinding binding = binding(current.workspace());
            Candidate candidate = network(binding, remote -> {
                ResolvedRef resolved = repository.resolve(remote, requested).orElseThrow(() -> refNotFound(requested));
                return read(remote, resolved, current.workspace(), model, actor);
            });
            ModelChanges changes = candidate.document()
                    .map(after -> ModelChanges.between(document(current.xml()), after))
                    .orElseGet(() -> new ModelChanges(List.of()));
            return new Preview(model, candidate.ref(), current.versionNo(), current.hash(),
                    candidate.session().sourceHash().value(), changes,
                    candidate.session().findings(), candidate.session().status() == ImportStatus.VALIDATED);
        });
    }

    /**
     * Применение коммита из отчёта новой версией. Модель изменилась после отчёта —
     * {@code 409}; разбор или строгий режим отклонили — {@code 422} с находками; отказ
     * при записи — откат и сессия {@code REJECTED}.
     */
    public Pulled apply(EditorIdentity actor, ModelId model, PullRequest request) {
        InterchangeOperation operation = InterchangeOperation.PULL_MODEL_FROM_GIT;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            GitRef ref = parse(() -> GitRef.of(required(request.ref(), "ref")));
            GitSha sha = parse(() -> GitSha.of(required(request.commitSha(), "commitSha")));
            String baseHash = required(request.baseHash(), "baseHash");
            if (!BASE_HASH.matcher(baseHash).matches()) {
                throw invalid(Message.of(InterchangeMessages.GIT_BASE_HASH_INVALID, baseHash));
            }
            WorkspaceId workspace = states.current(actor, model).workspace();
            GitBinding binding = binding(workspace);
            Fetched fetched = network(binding, remote -> {
                Optional<GitSha> head = repository.fetch(remote);
                ResolvedRef commit = repository.resolve(remote, GitRef.of(sha.value()))
                        .filter(r -> r.kind() == RefKind.COMMIT && r.sha().equals(sha))
                        .orElseThrow(() -> refNotFound(GitRef.of(sha.value())));
                return new Fetched(head, read(remote, new ResolvedRef(ref, commit.kind(), sha), workspace, model,
                        actor));
            });
            Candidate candidate = fetched.candidate();
            ImportSession session = candidate.session();
            if (session.status() == ImportStatus.REJECTED) {
                unitOfWork.write(() -> {
                    sessions.save(session);
                    return null;
                });
                metrics.importFinished(candidate.document().isEmpty() ? "rejected_corrupt" : "rejected_strict",
                        session.strictMode(), session.findings());
                throw rejected(session, candidate.document().isEmpty());
            }
            boolean adopt = fetched.head().filter(sha::equals).isPresent();
            String comment = request.comment().filter(c -> !c.isBlank()).orElseGet(() -> texts.text(
                    Message.of(InterchangeMessages.GIT_PULL_COMMENT, ref.value(), sha.value().substring(0, SHORT_SHA))));
            byte[] xml = candidate.xml().orElseThrow();
            Applied applied;
            try {
                applied = unitOfWork.write(() -> {
                    ModelStates.Current now = states.current(actor, model);
                    if (!now.hash().equals(baseHash)) {
                        throw new InterchangeException(InterchangeCodes.GIT_IMPORT, Failure.CONFLICT,
                                Message.of(InterchangeMessages.GIT_REPORT_STALE, model.value()), Map.of());
                    }
                    ModelStates.Replaced replaced = states.replace(actor, model, xml, comment);
                    Instant at = clock.instant();
                    ImportApplied event = session.apply(model, replaced.versionNo(), replaced.opaqueObjects(), at);
                    sessions.save(session);
                    if (adopt) {
                        adopt(binding, model, replaced, sha, at);
                    }
                    return new Applied(replaced, event);
                });
            } catch (RuntimeException failure) {
                recordFailure(session, failure);
                throw failure;
            }
            events.publish(List.of(applied.event()));
            metrics.importFinished("applied", session.strictMode(), session.findings());
            if (applied.replaced().created()) {
                metrics.opaqueObjects(model.value(), applied.replaced().opaqueObjects());
            }
            return new Pulled(session.id(), model, applied.replaced().versionNo(), applied.replaced().created(), sha,
                    session.findings());
        });
    }

    /** Голова ветки привязки в момент применения и прочитанный коммит. */
    private record Fetched(Optional<GitSha> head, Candidate candidate) {
    }

    private record Applied(ModelStates.Replaced replaced, ImportApplied event) {
    }

    /**
     * Принятая голова ветки: старые версии модели в очереди пропускаются, голова — последний
     * коммит привязки. Версия без изменений получает идентификатор головы сразу; новую
     * публикация признает по содержимому каталога.
     */
    private void adopt(GitBinding binding, ModelId model, ModelStates.Replaced replaced, GitSha head, Instant at) {
        long through = replaced.created() ? replaced.versionNo() - 1 : replaced.versionNo();
        outbox.supersede(model, through, at);
        if (!replaced.created()) {
            versions.recordGitSha(model, replaced.versionNo(), head);
        }
        bindings.save(bindings.find(binding.workspaceId()).orElse(binding).committed(head, at));
    }

    /** Коммит в сессию импорта: каталог модели — документ — снимок, разбор и проверка методологии. */
    private Candidate read(Remote remote, ResolvedRef ref, WorkspaceId workspace, ModelId model, EditorIdentity actor) {
        String directory = GitRemotes.modelDirectory(repository, remote, ref.sha(), model)
                .orElseThrow(() -> new InterchangeException(InterchangeCodes.GIT_MODEL_NOT_IN_REF, Failure.NOT_FOUND,
                        Message.of(InterchangeMessages.GIT_MODEL_NOT_IN_COMMIT, ref.sha().value(), model.value()), Map.of()));
        SortedMap<String, byte[]> files = repository.read(remote, ref.sha(), directory);
        Optional<ModelDocument> document;
        Optional<InvalidValueException> defect;
        try {
            document = Optional.of(reader.read(files));
            defect = Optional.empty();
        } catch (InvalidValueException unreadable) {
            document = Optional.empty();
            defect = Optional.of(unreadable);
        }
        Optional<byte[]> xml = document.map(d -> new ArchiXmlWriter().write(d));
        byte[] source = xml.orElseGet(() -> concatenated(files));
        ImportSession session = ImportSession.receive(new ImportSessionId(uuids.next()),
                request(workspace, model, ref, source, actor), clock.instant());
        if (defect.isPresent()) {
            session.reject(List.of(new ImportFinding(findingId(), Severity.ERROR, InterchangeCodes.CORRUPT_DOCUMENT,
                    defect.get().reason(), Optional.empty(), Optional.empty())), clock.instant());
            return new Candidate(ref, session, Optional.empty(), Optional.empty());
        }
        session.parse(new StaxArchiDocumentReader(), new ByteArrayInputStream(xml.orElseThrow()), this::findingId,
                clock.instant());
        if (session.status() == ImportStatus.REJECTED) {
            return new Candidate(ref, session, Optional.empty(), Optional.empty());
        }
        session.validate(methodology.check(session.document().orElseThrow(), this::findingId), clock.instant());
        return new Candidate(ref, session, session.document(), xml);
    }

    private ImportRequest request(WorkspaceId workspace, ModelId model, ResolvedRef ref, byte[] source,
                                  EditorIdentity actor) {
        String name = ref.ref().value() + "@" + ref.sha().value();
        return ImportRequest.of(workspace, Optional.of(model), name, source, uuids.next().toString(),
                policy.strictImport(workspace), actor.subject());
    }

    /**
     * Попытка, сорвавшаяся при записи, — в журнал отдельной транзакцией после отката:
     * новой сессией, потому что прежняя могла успеть перейти в {@code APPLIED} в памяти.
     */
    private void recordFailure(ImportSession attempt, RuntimeException failure) {
        String code = switch (failure) {
            case InterchangeException e -> e.code();
            case ModelingException e -> e.code();
            default -> InterchangeCodes.GIT_IMPORT;
        };
        ImportRequest request = new ImportRequest(attempt.workspaceId(), attempt.targetModelId(),
                attempt.sourceName(), attempt.sourceHash(), attempt.sourceSize(), uuids.next().toString(),
                attempt.strictMode(), attempt.startedBy());
        ImportSession rejected = ImportSession.receive(new ImportSessionId(uuids.next()), request, attempt.startedAt());
        rejected.reject(List.of(new ImportFinding(findingId(), Severity.ERROR, code,
                Message.of(InterchangeMessages.GIT_PULL_FAILED, code), Optional.empty(), Optional.empty())),
                clock.instant());
        unitOfWork.write(() -> {
            sessions.save(rejected);
            return null;
        });
        metrics.importFinished("rejected_apply", attempt.strictMode(), rejected.findings());
    }

    private GitBinding binding(WorkspaceId workspace) {
        return unitOfWork.read(() -> bindings.find(workspace)).filter(GitBinding::writable)
                .orElseThrow(() -> new InterchangeException(InterchangeCodes.GIT_NOT_BOUND, Failure.CONFLICT,
                        Message.of(InterchangeMessages.GIT_BINDING_MISSING, workspace), Map.of()));
    }

    /** Сетевая часть: недоступный репозиторий — {@code 503}, модель не тронута. */
    private <T> T network(GitBinding binding, java.util.function.Function<Remote, T> work) {
        try {
            return work.apply(remotes.remote(binding));
        } catch (Unreachable e) {
            throw new InterchangeException(InterchangeCodes.GIT_UNREACHABLE, Failure.UNAVAILABLE,
                    Message.of(InterchangeMessages.GIT_UNREACHABLE, binding.repoUrl()), Map.of());
        }
    }

    private static ModelDocument document(byte[] xml) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(xml));
    }

    /** Источник сессии, когда документ не собрался: файлы каталога по порядку путей. */
    private static byte[] concatenated(SortedMap<String, byte[]> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        files.forEach((path, bytes) -> {
            out.writeBytes(path.getBytes(StandardCharsets.UTF_8));
            out.writeBytes(bytes);
        });
        return out.size() == 0 ? new byte[] {0} : out.toByteArray();
    }

    private static InterchangeException rejected(ImportSession session, boolean corrupt) {
        return new InterchangeException(corrupt ? InterchangeCodes.CORRUPT_DOCUMENT
                : InterchangeCodes.STRICT_IMPORT_REJECTED, Failure.UNPROCESSABLE,
                Message.of(corrupt ? InterchangeMessages.GIT_PULL_REJECTED_CORRUPT
                        : InterchangeMessages.GIT_PULL_REJECTED_STRICT),
                Map.of("sessionId", session.id().value(), "findings", session.findings()));
    }

    private static InterchangeException refNotFound(GitRef ref) {
        return new InterchangeException(InterchangeCodes.GIT_REF_NOT_FOUND, Failure.NOT_FOUND,
                Message.of(InterchangeMessages.GIT_REF_NOT_FOUND, ref.value()), Map.of());
    }

    private static String required(Optional<String> value, String field) {
        return value.filter(v -> !v.isBlank())
                .orElseThrow(() -> invalid(Message.of(InterchangeMessages.GIT_PULL_FIELD_MISSING, field)));
    }

    private static <T> T parse(Supplier<T> value) {
        try {
            return value.get();
        } catch (InvalidValueException e) {
            throw invalid(e.reason());
        }
    }

    private static InterchangeException invalid(Message reason) {
        return new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE, reason, Map.of());
    }

    private FindingId findingId() {
        return new FindingId(uuids.next());
    }
}
