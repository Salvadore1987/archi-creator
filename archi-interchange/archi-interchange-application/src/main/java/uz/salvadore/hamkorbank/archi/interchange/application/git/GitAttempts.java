package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.Function;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.importing.MethodologyCheck;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Remote;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Unreachable;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.port.SecretResolver;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.DomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ResolvedRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.UuidV7;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportRequest;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.Severity;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;

/**
 * Общее у сценариев, которые приносят состояние модели из Git новой версией, — импорта
 * по ссылке и слияния: привязка и сеть, каталог модели в коммите, сессия импорта
 * с разбором и проверкой методологии, запись отклонённой попытки.
 */
final class GitAttempts {

    private final GitBindingRepository bindings;
    private final GitRepositoryPort repository;
    private final GitRemotes remotes;
    private final ImportSessionRepository sessions;
    private final ImportPolicy policy;
    private final GitOutbox outbox;
    private final PublishedVersions versions;
    private final UnitOfWork unitOfWork;
    private final InterchangeMetrics metrics;
    private final Clock clock;
    private final UuidV7 uuids;
    private final GitYamlReader reader = new GitYamlReader();
    private final MethodologyCheck methodology = new MethodologyCheck();

    GitAttempts(GitBindingRepository bindings, GitRepositoryPort repository, SecretResolver secrets,
                ImportSessionRepository sessions, ImportPolicy policy, GitOutbox outbox, PublishedVersions versions,
                UnitOfWork unitOfWork, InterchangeMetrics metrics, TextCatalog texts, Clock clock) {
        this.bindings = bindings;
        this.outbox = outbox;
        this.versions = versions;
        this.repository = repository;
        this.remotes = new GitRemotes(secrets, texts);
        this.sessions = sessions;
        this.policy = policy;
        this.unitOfWork = unitOfWork;
        this.metrics = metrics;
        this.clock = clock;
        this.uuids = new UuidV7(clock);
    }

    /**
     * Каталог модели в коммите: файлы и документ, если собрался, иначе дефект сборки.
     * Из документа и дефекта задано ровно одно, другое — {@code null}.
     */
    record Tree(SortedMap<String, byte[]> files, ModelDocument document, InvalidValueException defect) {
    }

    /** Рабочая привязка пространства; нет — {@code 409}. */
    GitBinding binding(WorkspaceId workspace) {
        return unitOfWork.read(() -> bindings.find(workspace)).filter(GitBinding::writable)
                .orElseThrow(() -> new InterchangeException(InterchangeCodes.GIT_NOT_BOUND, Failure.CONFLICT,
                        Message.of(InterchangeMessages.GIT_BINDING_MISSING, workspace), Map.of()));
    }

    /** Сетевая часть: недоступный репозиторий — {@code 503}, модель не тронута. */
    <T> T network(GitBinding binding, Function<Remote, T> work) {
        try {
            return work.apply(remotes.remote(binding));
        } catch (Unreachable e) {
            throw new InterchangeException(InterchangeCodes.GIT_UNREACHABLE, Failure.UNAVAILABLE,
                    Message.of(InterchangeMessages.GIT_UNREACHABLE, binding.repoUrl()), Map.of());
        }
    }

    /** Каталог модели в коммите; нет — {@code 404}. */
    Tree tree(Remote remote, GitSha commit, ModelId model) {
        return directory(remote, commit, model).orElseThrow(() -> new InterchangeException(
                InterchangeCodes.GIT_MODEL_NOT_IN_REF, Failure.NOT_FOUND,
                Message.of(InterchangeMessages.GIT_MODEL_NOT_IN_COMMIT, commit.value(), model.value()), Map.of()));
    }

    /** Каталог модели в коммите; нет каталога — пусто. */
    Optional<Tree> directory(Remote remote, GitSha commit, ModelId model) {
        return GitRemotes.modelDirectory(repository, remote, commit, model).map(directory -> {
            SortedMap<String, byte[]> files = repository.read(remote, commit, directory);
            try {
                return new Tree(files, reader.read(files), null);
            } catch (InvalidValueException unreadable) {
                return new Tree(files, null, unreadable);
            }
        });
    }

    /** Сессия импорта с целевой моделью; источник — ссылка и коммит. */
    ImportSession receive(WorkspaceId workspace, ModelId model, ResolvedRef ref, byte[] source,
                          EditorIdentity actor) {
        String name = ref.ref().value() + "@" + ref.sha().value();
        return ImportSession.receive(new ImportSessionId(uuids.next()), ImportRequest.of(workspace,
                model, name, source, uuids.next().toString(), unitOfWork.read(() -> policy.strictImport(workspace)),
                actor.subject()), clock.instant());
    }

    /**
     * Принятая голова ветки — внутри транзакции записи: старые версии модели в очереди пропускаются,
     * голова — последний коммит привязки. Версия без изменений получает идентификатор головы сразу;
     * новую публикация признает по содержимому каталога. Привязку сменили, пока шла сеть, — голова
     * чужого теперь репозитория не принимается, очередь не трогается.
     *
     * @param used привязка, по которой читалась голова
     */
    void adopt(GitBinding used, ModelId model, ModelStates.Replaced replaced, GitSha head, Instant at) {
        Optional<GitBinding> current = bindings.find(used.workspaceId()).filter(b -> b.sameRemote(used));
        if (current.isEmpty()) {
            return;
        }
        long through = replaced.created() ? replaced.versionNo() - 1 : replaced.versionNo();
        outbox.supersede(model, through, at);
        if (!replaced.created()) {
            versions.recordGitSha(model, replaced.versionNo(), head);
        }
        bindings.save(current.get().committed(head, at));
    }

    /** Нечитаемое дерево — сразу {@code REJECTED} с находкой. */
    void corrupt(ImportSession session, InvalidValueException defect) {
        session.reject(List.of(new ImportFinding(findingId(), Severity.ERROR, InterchangeCodes.CORRUPT_DOCUMENT,
                defect.reason(), null, null)), clock.instant());
    }

    /** Разбор снимка и проверка методологии: сессия — {@code VALIDATED} или {@code REJECTED}. */
    void check(ImportSession session, byte[] xml) {
        session.parse(new StaxArchiDocumentReader(), new ByteArrayInputStream(xml), this::findingId, clock.instant());
        if (session.status() != ImportStatus.REJECTED) {
            session.validate(methodology.check(Objects.requireNonNull(session.document()), this::findingId),
                             clock.instant());
        }
    }

    /** Отклонённая до записи попытка — в журнал. */
    void saveRejected(ImportSession session, boolean corrupt) {
        unitOfWork.write(() -> {
            sessions.save(session);
            return null;
        });
        metrics.importFinished(corrupt ? "rejected_corrupt" : "rejected_strict", session.strictMode(),
                session.findings());
    }

    /**
     * Попытка, сорвавшаяся при записи, — в журнал отдельной транзакцией после отката:
     * новой сессией, потому что прежняя могла успеть перейти в {@code APPLIED} в памяти.
     * Сбой самого журнала прикладывается к отказу: клиент получает исходный отказ, а не сбой журнала.
     */
    void recordFailure(ImportSession attempt, RuntimeException failure) {
        try {
            journal(attempt, failure);
        } catch (RuntimeException journal) { // NOPMD - журнал попытки не заменяет собой отказ, ради которого пишется
            failure.addSuppressed(journal);
        }
    }

    private void journal(ImportSession attempt, RuntimeException failure) {
        String code = switch (failure) {
            case InterchangeException e -> e.code();
            case ModelingDomainException e -> e.code();
            case DomainException e -> e.code();
            default -> InterchangeCodes.GIT_APPLY_FAILED;
        };
        ImportRequest request = new ImportRequest(attempt.workspaceId(), attempt.targetModelId(),
                attempt.sourceName(), attempt.sourceHash(), attempt.sourceSize(), uuids.next().toString(),
                attempt.strictMode(), attempt.startedBy());
        ImportSession rejected = ImportSession.receive(new ImportSessionId(uuids.next()), request, attempt.startedAt());
        rejected.reject(List.of(new ImportFinding(findingId(), Severity.ERROR, code,
                Message.of(InterchangeMessages.GIT_PULL_FAILED, code), null, null)),
                clock.instant());
        unitOfWork.write(() -> {
            sessions.save(rejected);
            return null;
        });
        metrics.importFinished("rejected_apply", attempt.strictMode(), rejected.findings());
    }

    FindingId findingId() {
        return new FindingId(uuids.next());
    }

    static ModelDocument document(byte[] xml) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(xml));
    }

    /** Источник сессии, когда документ не собрался: файлы каталога по порядку путей. */
    static byte[] concatenated(SortedMap<String, byte[]> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        files.forEach((path, bytes) -> {
            out.writeBytes(path.getBytes(StandardCharsets.UTF_8));
            out.writeBytes(bytes);
        });
        return out.size() == 0 ? new byte[] {0} : out.toByteArray();
    }

    static InterchangeException refNotFound(GitRef ref) {
        return new InterchangeException(InterchangeCodes.GIT_REF_NOT_FOUND, Failure.NOT_FOUND,
                Message.of(InterchangeMessages.GIT_REF_NOT_FOUND, ref.value()), Map.of());
    }

    /** Обязательное поле запроса: {@code null} или пустое — отказ. */
    static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(Message.of(InterchangeMessages.GIT_PULL_FIELD_MISSING, field));
        }
        return value;
    }

    static <T> T parse(Supplier<T> value) {
        try {
            return value.get();
        } catch (InvalidValueException e) {
            throw invalid(e.reason());
        }
    }

    static InterchangeException invalid(Message reason) {
        return new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE, reason, Map.of());
    }
}
