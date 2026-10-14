package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.CommitRequest;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Person;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.PushResult;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Pushed;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Rejected;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Remote;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Unchanged;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Unreachable;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.port.SecretResolver;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.FileSlug;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitAuthor;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSnapshotCommitted;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Публикация зафиксированных версий в Git: коммит на версию, по порядку версий
 * внутри модели, только вперёд.
 *
 * <p>Сеть — вне транзакции; запись {@code git_sha} в версию, закрытие записи outbox
 * и новая голова привязки — одной транзакцией после push. Падение между ними
 * повтор находит по трейлерам коммита и не создаёт второго.
 *
 * <p>Чужой коммит, тронувший каталог модели, или переписанная история — расхождение:
 * сервер ничего не перезаписывает, очередь модели встаёт до импорта или слияния.
 * Чужие коммиты вне каталога расхождением не считаются: наш ложится сверху.
 */
public final class GitPublishingService {

    public static final String TRAILER_MODEL = "Archi-Model-Id";
    public static final String TRAILER_VERSION = "Archi-Version";
    static final String MODELS_DIR = GitRemotes.MODELS_DIR;
    static final String GITATTRIBUTES = ".gitattributes";
    private static final String GITATTRIBUTES_RESOURCE = "gitattributes";
    private static final int MAX_ATTEMPTS_SHIFT = 10;

    /** Кто коммитит и как выглядит автор, которого нет в привязке. */
    public record Settings(String committerName, String committerEmail, String authorEmailDomain,
                           Duration retryBase, Duration retryMax) {
    }

    /** Состояние модели в Git. */
    public record ModelGitStatus(ModelId model, State state, int pending, Optional<PublishedVersions.Published> last,
                                 Optional<Long> divergedVersionNo) {

        public enum State { SYNCED, PENDING, DIVERGED }
    }

    /** Итог одной попытки: запись закрыта, ждёт сети или встала на расхождении. */
    private enum Outcome { DONE, RETRY, DIVERGED }

    private final GitOutbox outbox;
    private final GitBindingRepository bindings;
    private final PublishedVersions versions;
    private final GitRepositoryPort repository;
    private final GitRemotes remotes;
    private final UnitOfWork unitOfWork;
    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final TextCatalog texts;
    private final Clock clock;
    private final Settings settings;
    private final GitYamlWriter writer = new GitYamlWriter();
    private final GitYamlReader reader = new GitYamlReader();
    private final Map<ModelId, Object> modelLocks = new ConcurrentHashMap<>();
    private final byte[] gitattributes;

    public GitPublishingService(GitOutbox outbox, GitBindingRepository bindings, PublishedVersions versions,
                                GitRepositoryPort repository, SecretResolver secrets, UnitOfWork unitOfWork,
                                InterchangeEvents events, InterchangeMetrics metrics, TextCatalog texts, Clock clock,
                                Settings settings) {
        this.outbox = outbox;
        this.bindings = bindings;
        this.versions = versions;
        this.repository = repository;
        this.remotes = new GitRemotes(secrets, texts);
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.metrics = metrics;
        this.texts = texts;
        this.clock = clock;
        this.settings = settings;
        this.gitattributes = resource(GITATTRIBUTES_RESOURCE);
    }

    /**
     * Проход очереди фоновым обработчиком: каждая модель — пока версии публикуются;
     * отказ сети откладывает модель, расхождение останавливает её до ручного разбора.
     *
     * @return сколько записей закрыто
     */
    public int publishPending() {
        int closed = 0;
        for (GitOutbox.Entry head : unitOfWork.read(outbox::heads)) {
            if (head.status() == GitOutbox.Status.DIVERGED
                    || head.nextAttemptAt().filter(at -> at.isAfter(clock.instant())).isPresent()) {
                continue;
            }
            closed += drain(head.model(), false);
        }
        return closed;
    }

    /**
     * Опубликовать очередь модели сейчас — после восстановления сети или после того,
     * как расхождение разобрали руками. Версию не создаёт.
     */
    public ModelGitStatus push(EditorIdentity actor, ModelId model) {
        InterchangeOperation operation = InterchangeOperation.PUSH_MODEL_TO_GIT;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            WorkspaceId workspace = versions.visibleWorkspace(actor, model);
            GitBinding binding = unitOfWork.read(() -> bindings.find(workspace)).filter(GitBinding::writable)
                    .orElseThrow(() -> new InterchangeException(InterchangeCodes.GIT_NOT_BOUND, Failure.CONFLICT,
                            Message.of(InterchangeMessages.GIT_BINDING_MISSING, workspace), Map.of()));
            drain(model, true);
            Optional<GitOutbox.Entry> head = unitOfWork.read(() -> outbox.head(model));
            GitBinding after = unitOfWork.read(() -> bindings.find(workspace)).orElse(binding);
            if (head.filter(h -> h.status() == GitOutbox.Status.PENDING).isPresent() && !after.reachable()) {
                throw new InterchangeException(InterchangeCodes.GIT_UNREACHABLE, Failure.UNAVAILABLE,
                        Message.of(InterchangeMessages.GIT_UNREACHABLE, after.repoUrl()), Map.of());
            }
            return status(model);
        });
    }

    public ModelGitStatus status(ModelId model) {
        return unitOfWork.read(() -> {
            Optional<GitOutbox.Entry> head = outbox.head(model);
            int open = outbox.open(model);
            Optional<PublishedVersions.Published> last = versions.lastPublished(model);
            if (head.isEmpty()) {
                return new ModelGitStatus(model, ModelGitStatus.State.SYNCED, 0, last, Optional.empty());
            }
            boolean diverged = head.get().status() == GitOutbox.Status.DIVERGED;
            return new ModelGitStatus(model, diverged ? ModelGitStatus.State.DIVERGED : ModelGitStatus.State.PENDING,
                    open, last, diverged ? Optional.of(head.get().versionNo()) : Optional.empty());
        });
    }

    /**
     * Настроен ли и доступен ли репозиторий пространства — живой проверкой, с записью
     * исхода в привязку. От ответа зависит очистка снимков: лучше не очистить, чем
     * очистить без копии.
     */
    public boolean reachable(WorkspaceId workspace) {
        Optional<GitBinding> binding = unitOfWork.read(() -> bindings.find(workspace)).filter(GitBinding::writable);
        if (binding.isEmpty()) {
            return false;
        }
        boolean reachable;
        try {
            repository.fetch(remote(binding.get()));
            reachable = true;
        } catch (Unreachable e) {
            reachable = false;
        }
        Instant at = clock.instant();
        boolean result = reachable;
        unitOfWork.write(() -> bindings.find(workspace).filter(GitBinding::writable)
                .ifPresent(b -> bindings.save(result ? b.checked(at) : b.unreachable(at))));
        return reachable;
    }

    /**
     * Снимок версии из её коммита: каталог модели, собранный обратно в {@code .archimate}.
     * Совпадение с отпечатком версии обязательно — иначе это не тот снимок, и он не отдаётся.
     *
     * @return пусто — привязки нет или каталога модели в коммите нет
     */
    public Optional<byte[]> restore(WorkspaceId workspace, ModelId model, long versionNo, GitSha sha,
                                    String contentHash) {
        Optional<GitBinding> binding = unitOfWork.read(() -> bindings.find(workspace));
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        try {
            Remote remote = remote(binding.get());
            repository.fetch(remote);
            Optional<String> directory = GitRemotes.modelDirectory(repository, remote, sha, model);
            if (directory.isEmpty()) {
                return Optional.empty();
            }
            byte[] xml = new ArchiXmlWriter().write(reader.read(repository.read(remote, sha, directory.get())));
            if (!ContentHash.of(xml).value().equals(contentHash)) {
                throw new InterchangeException(InterchangeCodes.EXPORT_VERSION, Failure.CONFLICT,
                        Message.of(InterchangeMessages.GIT_SNAPSHOT_MISMATCH, versionNo, sha), Map.of());
            }
            return Optional.of(xml);
        } catch (Unreachable e) {
            throw new InterchangeException(InterchangeCodes.GIT_UNREACHABLE, Failure.UNAVAILABLE,
                    Message.of(InterchangeMessages.GIT_UNREACHABLE, binding.get().repoUrl()), Map.of());
        }
    }

    /** Очередь одной модели по порядку, пока записи закрываются. */
    private int drain(ModelId model, boolean includeDiverged) {
        synchronized (modelLocks.computeIfAbsent(model, m -> new Object())) {
            int closed = 0;
            while (true) {
                Optional<GitOutbox.Entry> head = unitOfWork.read(() -> outbox.head(model));
                if (head.isEmpty() || head.get().status() == GitOutbox.Status.DIVERGED && !includeDiverged) {
                    return closed;
                }
                includeDiverged = false;
                if (publish(head.get()) != Outcome.DONE) {
                    return closed;
                }
                closed++;
            }
        }
    }

    private Outcome publish(GitOutbox.Entry entry) {
        Instant now = clock.instant();
        Optional<GitBinding> binding = unitOfWork.read(() -> bindings.find(entry.workspace()))
                .filter(GitBinding::writable);
        Optional<PublishedVersions.Version> version = unitOfWork.read(() ->
                versions.version(entry.model(), entry.versionNo()));
        if (binding.isEmpty() || version.isEmpty()) {
            unitOfWork.write(() -> {
                outbox.skipped(entry.id(), now);
                return null;
            });
            return Outcome.DONE;
        }
        long started = System.nanoTime();
        Outcome outcome = null;
        try {
            outcome = metrics.observe(InterchangeOperation.PUSH_MODEL_TO_GIT.useCase(),
                    () -> attempt(entry, binding.get(), version.get()));
            return outcome;
        } catch (Unreachable e) {
            outcome = Outcome.RETRY;
            Instant at = clock.instant();
            unitOfWork.write(() -> {
                bindings.find(entry.workspace()).filter(GitBinding::writable)
                        .ifPresent(b -> bindings.save(b.unreachable(at)));
                outbox.retryLater(entry.id(), e.getMessage(), at.plus(backoff(entry.attempts())));
                return null;
            });
            return Outcome.RETRY;
        } finally {
            metrics.gitPush(outcome == null ? "error" : outcome.name().toLowerCase(java.util.Locale.ROOT),
                    Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private Outcome attempt(GitOutbox.Entry entry, GitBinding binding, PublishedVersions.Version version) {
        Remote remote = remote(binding);
        Optional<GitSha> head = repository.fetch(remote);
        Map<String, String> trailers = Map.of(TRAILER_MODEL, entry.model().value().toString(),
                TRAILER_VERSION, Long.toString(entry.versionNo()));
        if (binding.lastCommitSha().isPresent()
                && (head.isEmpty() || !repository.contains(remote, binding.lastCommitSha().get(), head.get()))) {
            return diverged(entry, binding, head);
        }
        Optional<GitSha> already = head.flatMap(h -> repository.findByTrailers(remote, h, trailers));
        if (already.isPresent()) {
            return recorded(entry, binding, version, already.get(), head.orElseThrow(), 0);
        }
        String directory = directory(remote, head, entry.model(), version.modelName());
        if (head.isPresent()) {
            Optional<GitRepositoryPort.Commit> last = repository.lastChange(remote, head.get(), directory);
            if (last.isPresent() && !entry.model().value().toString().equals(last.get().trailers().get(TRAILER_MODEL))) {
                return diverged(entry, binding, head);
            }
        }
        SortedMap<String, byte[]> previous = head.map(h -> repository.read(remote, h, directory))
                .orElseGet(TreeMap::new);
        GitPositions positions;
        try {
            positions = previous.isEmpty() ? GitPositions.none() : reader.positions(previous);
        } catch (InvalidValueException unreadable) {
            positions = GitPositions.none();
        }
        ModelDocument document = new StaxArchiDocumentReader().read(new ByteArrayInputStream(version.xml()));
        SortedMap<String, byte[]> files = writer.write(document, positions);
        SortedMap<String, byte[]> rootFiles = new TreeMap<>();
        rootFiles.put(GITATTRIBUTES, gitattributes(head.flatMap(h -> repository.file(remote, h, GITATTRIBUTES))));
        CommitRequest request = new CommitRequest(directory, files, rootFiles, message(version, trailers),
                author(binding, version), new Person(settings.committerName(), settings.committerEmail(),
                clock.instant()));
        PushResult result = repository.push(remote, head, request);
        return switch (result) {
            case Pushed pushed -> recorded(entry, binding, version, pushed.sha(), pushed.sha(), pushed.changedFiles());
            case Unchanged unchanged -> recorded(entry, binding, version, unchanged.head(), unchanged.head(), 0);
            case Rejected rejected -> {
                unitOfWork.write(() -> {
                    outbox.retryLater(entry.id(), rejected.toString(), clock.instant());
                    return null;
                });
                yield Outcome.RETRY;
            }
        };
    }

    /** {@code git_sha}, запись outbox и голова привязки — одной транзакцией; событие — после неё. */
    private Outcome recorded(GitOutbox.Entry entry, GitBinding binding, PublishedVersions.Version version, GitSha sha,
                             GitSha newHead, int changedFiles) {
        Instant at = clock.instant();
        unitOfWork.write(() -> {
            versions.recordGitSha(entry.model(), entry.versionNo(), sha);
            outbox.done(entry.id(), at);
            GitBinding current = bindings.find(entry.workspace()).orElse(binding);
            if (current.writable()) {
                bindings.save(current.committed(newHead, at));
            }
            return null;
        });
        events.publish(List.of(new GitSnapshotCommitted(version.workspace(), entry.model(), entry.versionNo(), sha,
                binding.branch(), changedFiles, at)));
        return Outcome.DONE;
    }

    private Outcome diverged(GitOutbox.Entry entry, GitBinding binding, Optional<GitSha> head) {
        Instant at = clock.instant();
        unitOfWork.write(() -> {
            outbox.diverged(entry.id(), head.map(GitSha::value).orElse(""), at);
            bindings.find(entry.workspace()).filter(GitBinding::writable).ifPresent(b -> bindings.save(b.checked(at)));
            return null;
        });
        return Outcome.DIVERGED;
    }

    private Remote remote(GitBinding binding) {
        return remotes.remote(binding);
    }

    /** Каталог модели: найденный в ветке по короткому идентификатору — прежний, иначе по имени. */
    private String directory(Remote remote, Optional<GitSha> head, ModelId model, String modelName) {
        return head.flatMap(h -> GitRemotes.modelDirectory(repository, remote, h, model))
                .orElseGet(() -> MODELS_DIR + "/" + FileSlug.slug(modelName) + "." + GitRemotes.shortId(model));
    }

    private String message(PublishedVersions.Version version, Map<String, String> trailers) {
        String subject = version.comment().filter(c -> !c.isBlank()).orElseGet(() ->
                texts.text(Message.of(InterchangeMessages.GIT_COMMIT_MESSAGE, version.versionNo())));
        return subject.strip() + "\n\n" + TRAILER_MODEL + ": " + trailers.get(TRAILER_MODEL) + "\n"
                + TRAILER_VERSION + ": " + trailers.get(TRAILER_VERSION) + "\n";
    }

    /** Автор — архитектор, сохранивший версию: по привязке, иначе субъект в домене из настройки. */
    private Person author(GitBinding binding, PublishedVersions.Version version) {
        Optional<GitAuthor> mapped = binding.authorOf(version.author());
        return mapped.map(a -> new Person(a.name(), a.email(), version.createdAt()))
                .orElseGet(() -> new Person(version.author(),
                        version.author() + "@" + settings.authorEmailDomain(), version.createdAt()));
    }

    /** Правило слияния ставится, не затирая чужих строк файла. */
    private byte[] gitattributes(Optional<byte[]> existing) {
        if (existing.isEmpty()) {
            return gitattributes;
        }
        String text = new String(existing.get(), StandardCharsets.UTF_8);
        String rule = new String(gitattributes, StandardCharsets.UTF_8).lines()
                .filter(l -> !l.isBlank() && !l.startsWith("#")).findFirst().orElseThrow();
        if (text.lines().anyMatch(l -> l.strip().equals(rule))) {
            return existing.get();
        }
        return (text + (text.isEmpty() || text.endsWith("\n") ? "" : "\n") + rule + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private Duration backoff(int attempts) {
        Duration delay = settings.retryBase().multipliedBy(1L << Math.min(attempts, MAX_ATTEMPTS_SHIFT));
        return delay.compareTo(settings.retryMax()) > 0 ? settings.retryMax() : delay;
    }

    private static byte[] resource(String name) {
        try (InputStream in = GitPublishingService.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
