package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Remote;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.port.SecretResolver;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ModelChanges;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ResolvedRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportSession;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

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
 *
 * <p>Шаги сценария, а не сценарий целиком: роль и метрику даёт handler, который зовёт этот класс.
 */
public final class GitPull {


    /**
     * Что подтверждает архитектор: ссылка и коммит из отчёта, отпечаток содержимого, комментарий.
     * Не переданное поле — {@code null}; без комментария версия получает комментарий по умолчанию.
     */
    public record PullRequest(@Nullable String ref, @Nullable String commitSha, @Nullable String baseHash,
                              @Nullable String comment) {
    }

    /**
     * Отчёт до применения.
     *
     * @param baseVersionNo последняя версия модели; у модели без версий {@code null}
     * @param baseHash      отпечаток текущего содержимого — его применение и подтверждает
     * @param applicable    применение пройдёт разбор и строгий режим пространства
     */
    public record Preview(ModelId model, ResolvedRef ref, @Nullable Long baseVersionNo, String baseHash,
                          String targetHash, ModelChanges changes, List<ImportFinding> findings, boolean applicable) {

        public Preview {
            findings = List.copyOf(findings);
        }

        /** Содержимое по ссылке совпадает с текущим: применение версии не создаст. */
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

    /**
     * Прочитанный коммит: сессия после разбора и проверки, документ и снимок, если приняты, иначе {@code null}.
     *
     * @param corrupt каталог не собрался или снимок не разобрался; отказ строгого режима — не повреждение
     */
    private record Candidate(ResolvedRef ref, ImportSession session, @Nullable ModelDocument document,
                             byte @Nullable [] xml, boolean corrupt) {
    }

    private final GitBindingRepository bindings;
    private final GitRepositoryPort repository;
    private final GitAttempts attempts;
    private final ModelStates states;
    private final ImportSessionRepository sessions;
    private final GitOutbox outbox;
    private final PublishedVersions versions;
    private final UnitOfWork unitOfWork;
    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final Clock clock;

    public GitPull(GitBindingRepository bindings, GitRepositoryPort repository, SecretResolver secrets,
                            ModelStates states, ImportSessionRepository sessions, ImportPolicy policy,
                            GitOutbox outbox, PublishedVersions versions, UnitOfWork unitOfWork,
                            InterchangeEvents events, InterchangeMetrics metrics, TextCatalog texts, Clock clock) {
        this.bindings = bindings;
        this.repository = repository;
        this.attempts = new GitAttempts(bindings, repository, secrets, sessions, policy, outbox, versions, unitOfWork,
                metrics, texts, clock);
        this.states = states;
        this.sessions = sessions;
        this.outbox = outbox;
        this.versions = versions;
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Отчёт об изменениях по ссылке ({@code null} — не задана, отказ). В базе ничего не меняется. */
    public Preview preview(EditorIdentity actor, ModelId model, @Nullable String ref) {
        GitRef requested = GitAttempts.parse(() -> GitRef.of(GitAttempts.required(ref, "ref")));
        ModelStates.Current current = states.current(actor, model);
        GitBinding binding = attempts.binding(current.workspace());
        Candidate candidate = attempts.network(binding, remote -> {
            ResolvedRef resolved = repository.resolve(remote, requested)
                    .orElseThrow(() -> GitAttempts.refNotFound(requested));
            return read(remote, resolved, current.workspace(), model, actor);
        });
        ModelChanges changes = candidate.document() == null ? new ModelChanges(List.of())
                : ModelChanges.between(GitAttempts.document(current.xml()), candidate.document());
        return new Preview(model, candidate.ref(), current.versionNo(), current.hash(),
                candidate.session().sourceHash().value(), changes,
                candidate.session().findings(), candidate.session().status() == ImportStatus.VALIDATED);
    }

    /**
     * Применение коммита из отчёта новой версией. Нет привязки — {@code 409}; коммита или каталога
     * модели в нём нет — {@code 404}; репозиторий недоступен — {@code 503}. Модель изменилась после
     * отчёта или не заблокирована — {@code 409}; разбор или строгий режим отклонили — {@code 422}
     * с находками. Любой отказ после чтения коммита — откат и сессия {@code REJECTED}.
     */
    public Pulled apply(EditorIdentity actor, ModelId model, PullRequest request) {
        GitRef ref = GitAttempts.parse(() -> GitRef.of(GitAttempts.required(request.ref(), "ref")));
        GitSha sha = GitAttempts.parse(() -> GitSha.of(GitAttempts.required(request.commitSha(), "commitSha")));
        String baseHash = GitAttempts.baseHash(request.baseHash());
        WorkspaceId workspace = states.current(actor, model).workspace();
        GitBinding binding = attempts.binding(workspace);
        Fetched fetched = attempts.network(binding, remote -> {
            GitSha head = repository.fetch(remote).orElse(null);
            ResolvedRef commit = attempts.commit(remote, sha);
            return new Fetched(head, read(remote, new ResolvedRef(ref, commit.kind(), sha), workspace, model,
                    actor));
        });
        Candidate candidate = fetched.candidate();
        ImportSession session = candidate.session();
        if (session.status() == ImportStatus.REJECTED) {
            attempts.saveRejected(session, candidate.corrupt());
            throw rejected(session, candidate.corrupt());
        }
        boolean adopt = sha.equals(fetched.head());
        String comment = attempts.comment(request.comment(), InterchangeMessages.GIT_PULL_COMMENT, ref, sha);
        byte[] xml = Objects.requireNonNull(candidate.xml());
        Applied applied;
        try {
            applied = unitOfWork.write(() -> {
                GitAttempts.requireUnchanged(states.current(actor, model), baseHash, model);
                ModelStates.Replaced replaced = states.replace(actor, model, xml, comment);
                Instant at = clock.instant();
                session.apply(model, replaced.versionNo(), replaced.opaqueObjects(), at);
                sessions.save(session);
                if (adopt) {
                    attempts.adopt(binding, model, replaced, sha, at);
                }
                return new Applied(replaced, session.pullEvents());
            });
        } catch (RuntimeException failure) { // NOPMD - любой отказ записи — в журнал попыток, и дальше
            attempts.recordFailure(session, failure);
            throw failure;
        }
        events.publish(applied.events());
        metrics.importFinished("applied", session.strictMode(), session.findings());
        if (applied.replaced().created()) {
            metrics.opaqueObjects(model.value(), applied.replaced().opaqueObjects());
        }
        return new Pulled(session.id(), model, applied.replaced().versionNo(), applied.replaced().created(), sha,
                session.findings());
    }

    /** Голова ветки привязки в момент применения ({@code null} — ветки нет) и прочитанный коммит. */
    private record Fetched(@Nullable GitSha head, Candidate candidate) {
    }

    private record Applied(ModelStates.Replaced replaced, List<DomainEvent> events) {
    }

    /** Коммит в сессию импорта: каталог модели — документ — снимок, разбор и проверка методологии. */
    private Candidate read(Remote remote, ResolvedRef ref, WorkspaceId workspace, ModelId model, EditorIdentity actor) {
        GitAttempts.Tree tree = attempts.tree(remote, ref.sha(), model);
        byte[] xml = tree.document() == null ? null : new ArchiXmlWriter().write(tree.document());
        ImportSession session = attempts.receive(workspace, model, ref,
                xml != null ? xml : GitAttempts.concatenated(tree.files()), actor);
        if (tree.defect() != null) {
            attempts.corrupt(session, tree.defect());
            return new Candidate(ref, session, null, null, true);
        }
        attempts.check(session, Objects.requireNonNull(xml));
        if (session.status() == ImportStatus.REJECTED) {
            return new Candidate(ref, session, null, null, session.document() == null);
        }
        return new Candidate(ref, session, session.document(), xml, false);
    }

    private static InterchangeException rejected(ImportSession session, boolean corrupt) {
        return new InterchangeException(corrupt ? InterchangeCodes.CORRUPT_DOCUMENT
                : InterchangeCodes.STRICT_IMPORT_REJECTED, Failure.UNPROCESSABLE,
                Message.of(corrupt ? InterchangeMessages.GIT_PULL_REJECTED_CORRUPT
                        : InterchangeMessages.GIT_PULL_REJECTED_STRICT),
                Map.of("sessionId", session.id().value(), "findings", session.findings()));
    }
}
