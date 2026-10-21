package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitOutbox;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Remote;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportPolicy;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeEvents;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelMerges;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.application.port.PublishedVersions;
import uz.salvadore.hamkorbank.archi.interchange.application.port.SecretResolver;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.ArchiXmlWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.DomainEvent;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.EntityMerge.Side;
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
 * Слияние предложения из ветки по сущностям — новой версией модели.
 *
 * <p>Три состояния: «наше» — текущее содержимое модели в базе, «их» — каталог модели
 * в коммите ссылки, база — каталог в общем предке этого коммита и коммита последней
 * опубликованной версии. Слияние — {@link EntityMerge}: по объектам и полям, конфликты
 * разрешает архитектор. Отчёт ничего не пишет и принимает выборы, чтобы показать их итог;
 * применение берёт из отчёта оба коммита и отпечаток содержимого и строит слияние заново.
 *
 * <p>Применение — одна транзакция, как у импорта по ссылке, и ещё слитый коммит за моделью:
 * его правки публикация больше не считает расхождением, а сам он станет вторым родителем.
 * Слияние коммита, в котором есть голова ветки привязки, снимает расхождение, если очередь
 * модели на нём стоит: старые версии в очереди пропускаются — новая их содержит.
 *
 * <p>Шаги сценария, а не сценарий целиком: роль и метрику даёт handler, который зовёт этот класс.
 */
public final class GitMerge {

    private static final String BOUNDS = "bounds";
    private static final String ELEMENT_REF = "archimateElement";

    /**
     * Что подтверждает архитектор: ссылка, оба коммита и отпечаток из отчёта, выборы, комментарий.
     * Не переданное поле — {@code null}; без комментария версия получает комментарий по умолчанию.
     * Выбор без значения сохраняется как есть: отказывает в нём сценарий, а не конструктор.
     */
    public record MergeRequest(String ref, String commitSha, String mergeBaseSha, String baseHash,
                               Map<String, String> resolutions, String comment) {

        public MergeRequest {
            resolutions = resolutions == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(resolutions));
        }
    }

    /** Отметка объекта для холста: модель, папка, элемент, связь, представление, узел или ребро. */
    public record Mark(ArchiId archiId, ModelChanges.Type mark) {
    }

    /**
     * Узел, которого в текущем содержимом нет, — с абсолютной геометрией.
     *
     * @param element элемент модели узла; {@code null} — узел без элемента
     * @param type    тип без префикса {@code archimate:}; {@code null} — неизвестен
     */
    public record AddedNode(ArchiId archiId, ArchiId element, String type, String name,
                            int x, int y, int width, int height) {
    }

    /** Узлы, которые слияние добавит в представление. */
    public record ViewAdditions(ArchiId view, List<AddedNode> nodes) {

        public ViewAdditions {
            nodes = List.copyOf(nodes);
        }
    }

    /**
     * Отчёт до применения.
     *
     * @param baseVersionNo последняя версия модели; у модели без версий {@code null}
     * @param baseHash   отпечаток текущего содержимого — его применение и подтверждает
     * @param targetHash отпечаток результата с выборами отчёта
     * @param applicable конфликты разрешены, разбор и строгий режим пройдут
     */
    public record Preview(ModelId model, ResolvedRef ref, GitSha mergeBase, Long baseVersionNo,
                          String baseHash, String targetHash, List<EntityMerge.Conflict> conflicts,
                          List<EntityMerge.Cascade> cascade, ModelChanges changes, List<Mark> marks,
                          List<ViewAdditions> views, List<ImportFinding> findings, boolean applicable) {

        public Preview {
            conflicts = List.copyOf(conflicts);
            cascade = List.copyOf(cascade);
            marks = List.copyOf(marks);
            views = List.copyOf(views);
            findings = List.copyOf(findings);
        }

        /** Итог слияния совпадает с текущим содержимым: применение версии не создаст. */
        public boolean unchanged() {
            return baseHash.equals(targetHash);
        }
    }

    /** @param created ложь — результат совпал с текущим, версия прежняя */
    public record Merged(ImportSessionId session, ModelId model, long versionNo, boolean created, GitSha commit,
                         List<ImportFinding> findings) {

        public Merged {
            findings = List.copyOf(findings);
        }
    }

    /** Прочитанные состояния: ссылка, предок, деревья «их» и базы, голова ветки ({@code null} — не читалась). */
    private record Sides(ResolvedRef ref, GitSha mergeBase, GitAttempts.Tree theirs, GitAttempts.Tree base,
                         GitSha head, boolean absorbsHead) {

        Optional<GitAttempts.Tree> corrupt() {
            return theirs.defect() != null ? Optional.of(theirs)
                    : base.defect() != null ? Optional.of(base) : Optional.empty();
        }
    }

    /** Результат слияния в сессии импорта: снимок и проверка. */
    private record Attempt(EntityMerge.Result result, byte[] xml, ImportSession session) {
    }

    private final GitBindingRepository bindings;
    private final GitRepositoryPort repository;
    private final GitAttempts attempts;
    private final ModelStates states;
    private final ImportSessionRepository sessions;
    private final GitOutbox outbox;
    private final PublishedVersions versions;
    private final ModelMerges merges;
    private final UnitOfWork unitOfWork;
    private final InterchangeEvents events;
    private final InterchangeMetrics metrics;
    private final Clock clock;

    public GitMerge(GitBindingRepository bindings, GitRepositoryPort repository, SecretResolver secrets,
                           ModelStates states, ImportSessionRepository sessions, ImportPolicy policy,
                           GitOutbox outbox, PublishedVersions versions, ModelMerges merges, UnitOfWork unitOfWork,
                           InterchangeEvents events, InterchangeMetrics metrics, TextCatalog texts, Clock clock) {
        this.bindings = bindings;
        this.repository = repository;
        this.attempts = new GitAttempts(bindings, repository, secrets, sessions, policy, outbox, versions, unitOfWork,
                metrics, texts, clock);
        this.states = states;
        this.sessions = sessions;
        this.outbox = outbox;
        this.versions = versions;
        this.merges = merges;
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Отчёт слияния по ссылке ({@code null} — не задана, отказ) с выборами по конфликтам.
     * В базе ничего не меняется.
     */
    public Preview preview(EditorIdentity actor, ModelId model, String ref,
                           Map<String, String> resolutions) {
        GitRef requested = GitAttempts.parse(() -> GitRef.of(GitAttempts.required(ref, "ref")));
        Map<String, Side> choices = choices(resolutions);
        ModelStates.Current current = states.current(actor, model);
        GitBinding binding = attempts.binding(current.workspace());
        GitSha published = published(model);
        Sides sides = attempts.network(binding, remote -> {
            ResolvedRef resolved = repository.resolve(remote, requested)
                    .orElseThrow(() -> GitAttempts.refNotFound(requested));
            GitSha mergeBase = repository.mergeBase(remote, published, resolved.sha())
                    .orElseThrow(() -> noMergeBase(resolved.sha(), model));
            return read(remote, resolved, mergeBase, model, false);
        });
        ModelDocument ours = GitAttempts.document(current.xml());
        Optional<GitAttempts.Tree> corrupt = sides.corrupt();
        if (corrupt.isPresent()) {
            ImportSession session = attempts.receive(current.workspace(), model, sides.ref(),
                    GitAttempts.concatenated(corrupt.get().files()), actor);
            attempts.corrupt(session, Objects.requireNonNull(corrupt.get().defect()));
            return new Preview(model, sides.ref(), sides.mergeBase(), current.versionNo(), current.hash(),
                    session.sourceHash().value(), List.of(), List.of(), new ModelChanges(List.of()), List.of(),
                    List.of(), session.findings(), false);
        }
        Attempt attempt = attempt(current.workspace(), model, sides, ours, choices, actor);
        EntityMerge.Result result = attempt.result();
        ModelChanges changes = ModelChanges.between(ours, result.document());
        return new Preview(model, sides.ref(), sides.mergeBase(), current.versionNo(), current.hash(),
                ContentHash.of(attempt.xml()).value(), result.conflicts(), result.cascade(), changes,
                marks(ours, result.document(), changes), additions(ours, result.document()),
                attempt.session().findings(),
                result.resolved() && attempt.session().status() == ImportStatus.VALIDATED);
    }

    /**
     * Применение слияния из отчёта новой версией. Нет привязки — {@code 409}; коммита или каталога
     * модели нет — {@code 404}; репозиторий недоступен — {@code 503}. Конфликт без выбора — {@code 422};
     * модель изменилась после отчёта или не заблокирована — {@code 409}; разбор или строгий режим
     * отклонили — {@code 422} с находками. Отказ при записи — откат и сессия {@code REJECTED}.
     */
    public Merged apply(EditorIdentity actor, ModelId model, MergeRequest request) {
        GitRef ref = GitAttempts.parse(() -> GitRef.of(GitAttempts.required(request.ref(), "ref")));
        GitSha sha = GitAttempts.parse(() -> GitSha.of(GitAttempts.required(request.commitSha(), "commitSha")));
        GitSha base = GitAttempts.parse(() -> GitSha.of(GitAttempts.required(request.mergeBaseSha(),
                "mergeBaseSha")));
        String baseHash = GitAttempts.baseHash(request.baseHash());
        Map<String, Side> choices = choices(request.resolutions());
        ModelStates.Current current = states.current(actor, model);
        GitBinding binding = attempts.binding(current.workspace());
        Sides sides = attempts.network(binding, remote -> {
            ResolvedRef commit = attempts.commit(remote, sha);
            if (!repository.contains(remote, base, commit.sha())) {
                throw GitAttempts.invalid(Message.of(InterchangeMessages.GIT_MERGE_BASE_INVALID, base.value(),
                        sha.value()));
            }
            return read(remote, new ResolvedRef(ref, commit.kind(), sha), base, model, true);
        });
        Optional<GitAttempts.Tree> corrupt = sides.corrupt();
        if (corrupt.isPresent()) {
            ImportSession session = attempts.receive(current.workspace(), model, sides.ref(),
                    GitAttempts.concatenated(corrupt.get().files()), actor);
            attempts.corrupt(session, Objects.requireNonNull(corrupt.get().defect()));
            attempts.saveRejected(session, true);
            throw rejected(session, Message.of(InterchangeMessages.GIT_MERGE_REJECTED_CORRUPT,
                    sides.ref().sha().value()), InterchangeCodes.CORRUPT_DOCUMENT);
        }
        Attempt attempt = attempt(current.workspace(), model, sides, GitAttempts.document(current.xml()),
                choices, actor);
        if (!attempt.result().resolved()) {
            List<String> unresolved = attempt.result().unresolved();
            throw new InterchangeException(InterchangeCodes.MERGE_CONFLICTS_UNRESOLVED, Failure.UNPROCESSABLE,
                    Message.of(InterchangeMessages.GIT_MERGE_UNRESOLVED, String.join(", ", unresolved)),
                    Map.of("conflicts", unresolved));
        }
        ImportSession session = attempt.session();
        if (session.status() == ImportStatus.REJECTED) {
            attempts.saveRejected(session, false);
            throw rejected(session, Message.of(InterchangeMessages.GIT_PULL_REJECTED_STRICT),
                    InterchangeCodes.STRICT_IMPORT_REJECTED);
        }
        String comment = attempts.comment(request.comment(), InterchangeMessages.GIT_MERGE_COMMENT, ref, sha);
        Applied applied;
        try {
            applied = unitOfWork.write(() -> {
                GitAttempts.requireUnchanged(states.current(actor, model), baseHash, model);
                ModelStates.Replaced replaced = states.replace(actor, model, attempt.xml(), comment);
                Instant at = clock.instant();
                session.apply(model, replaced.versionNo(), replaced.opaqueObjects(), at);
                sessions.save(session);
                merges.record(model, sha, at);
                if (sides.absorbsHead() && outbox.head(model)
                        .filter(h -> h.status() == GitOutbox.Status.DIVERGED).isPresent()) {
                    attempts.adopt(binding, model, replaced, Objects.requireNonNull(sides.head()), at);
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
        return new Merged(session.id(), model, applied.replaced().versionNo(), applied.replaced().created(), sha,
                session.findings());
    }

    private record Applied(ModelStates.Replaced replaced, List<DomainEvent> events) {
    }

    /**
     * Деревья «их» и базы. Каталога модели в предке нет — сливать не с чем. При применении
     * ещё и голова ветки привязки: слияние, которое её содержит, снимает расхождение.
     */
    private Sides read(Remote remote, ResolvedRef ref, GitSha mergeBase, ModelId model, boolean withHead) {
        GitSha head = withHead ? repository.fetch(remote).orElse(null) : null;
        GitAttempts.Tree theirs = attempts.tree(remote, ref.sha(), model);
        GitAttempts.Tree base = attempts.directory(remote, mergeBase, model)
                .orElseThrow(() -> noMergeBase(ref.sha(), model));
        boolean absorbsHead = head != null && repository.contains(remote, head, ref.sha());
        return new Sides(ref, mergeBase, theirs, base, head, absorbsHead);
    }

    /** Слияние с выборами — в сессию импорта: снимок, разбор, методология. */
    private Attempt attempt(WorkspaceId workspace, ModelId model, Sides sides, ModelDocument ours,
                            Map<String, Side> choices, EditorIdentity actor) {
        EntityMerge.Result result = EntityMerge.merge(Objects.requireNonNull(sides.base().document()), ours,
                Objects.requireNonNull(sides.theirs().document()), choices);
        List<String> unknown = result.unknown(choices.keySet());
        if (!unknown.isEmpty()) {
            throw GitAttempts.invalid(Message.of(InterchangeMessages.GIT_MERGE_RESOLUTION_UNKNOWN,
                    String.join(", ", unknown)));
        }
        byte[] xml = new ArchiXmlWriter().write(result.document());
        ImportSession session = attempts.receive(workspace, model, sides.ref(), xml, actor);
        attempts.check(session, xml);
        return new Attempt(result, xml, session);
    }

    private GitSha published(ModelId model) {
        return unitOfWork.read(() -> versions.lastPublished(model)).map(PublishedVersions.Published::sha)
                .orElseThrow(() -> new InterchangeException(InterchangeCodes.GIT_NO_MERGE_BASE, Failure.CONFLICT,
                        Message.of(InterchangeMessages.GIT_NO_PUBLISHED_VERSION, model.value()), Map.of()));
    }

    private static Map<String, Side> choices(Map<String, String> resolutions) {
        Map<String, Side> choices = new LinkedHashMap<>();
        resolutions.forEach((id, value) -> {
            if (value == null) {
                throw GitAttempts.invalid(Message.of(InterchangeMessages.GIT_MERGE_RESOLUTION_INVALID, id, ""));
            }
            try {
                choices.put(id, Side.valueOf(String.valueOf(value)));
            } catch (IllegalArgumentException unknown) {
                throw GitAttempts.invalid(Message.of(InterchangeMessages.GIT_MERGE_RESOLUTION_INVALID, id, value));
            }
        });
        return choices;
    }

    /**
     * Отметки результата относительно текущего содержимого: объекты модели из отчёта
     * об изменениях и узлы с рёбрами изменённых представлений.
     */
    private static List<Mark> marks(ModelDocument ours, ModelDocument merged, ModelChanges changes) {
        List<Mark> marks = new ArrayList<>();
        changes.changes().forEach(change -> marks.add(new Mark(change.archiId(), change.type())));
        for (DocumentNode view : merged.views()) {
            Optional<DocumentNode> before = ours.find(view.archiId());
            if (before.isEmpty()) {
                continue;
            }
            Map<ArchiId, DocumentNode> old = diagram(before.get());
            Map<ArchiId, DocumentNode> now = diagram(view);
            now.forEach((id, node) -> {
                DocumentNode previous = old.get(id);
                if (previous == null) {
                    marks.add(new Mark(id, ModelChanges.Type.ADDED));
                } else if (!own(previous).equals(own(node))) {
                    marks.add(new Mark(id, ModelChanges.Type.CHANGED));
                }
            });
            old.keySet().stream().filter(id -> !now.containsKey(id))
                    .forEach(id -> marks.add(new Mark(id, ModelChanges.Type.REMOVED)));
        }
        for (DocumentNode view : ours.views()) {
            if (merged.find(view.archiId()).isEmpty()) {
                diagram(view).keySet().forEach(id -> marks.add(new Mark(id, ModelChanges.Type.REMOVED)));
            }
        }
        return marks;
    }

    /** Узлы представлений результата, которых в текущем содержимом нет, — с абсолютной геометрией. */
    private static List<ViewAdditions> additions(ModelDocument ours, ModelDocument merged) {
        List<ViewAdditions> views = new ArrayList<>();
        for (DocumentNode view : merged.views()) {
            Map<ArchiId, DocumentNode> old = ours.find(view.archiId()).map(GitMerge::diagram)
                    .orElseGet(Map::of);
            List<AddedNode> added = new ArrayList<>();
            collectAdded(view.children(), 0, 0, old, merged, added);
            if (!added.isEmpty()) {
                views.add(new ViewAdditions(view.archiId(), added));
            }
        }
        return views;
    }

    private static void collectAdded(List<DocumentNode> nodes, int originX, int originY,
                                     Map<ArchiId, DocumentNode> old, ModelDocument merged, List<AddedNode> into) {
        for (DocumentNode node : nodes) {
            Optional<DocumentValue> bounds = node.values(BOUNDS).stream().findFirst();
            if (bounds.isEmpty()) {
                continue;
            }
            int x = originX + number(bounds.get(), "x");
            int y = originY + number(bounds.get(), "y");
            if (!old.containsKey(node.archiId())) {
                Optional<ArchiId> element = node.attribute(ELEMENT_REF).map(ArchiId::of);
                Optional<DocumentNode> concept = element.flatMap(merged::find);
                into.add(new AddedNode(node.archiId(), element.orElse(null),
                        concept.flatMap(DocumentNode::archiType).or(node::archiType).map(GitMerge::bareType)
                                .orElse(null),
                        node.attribute("name").or(() -> concept.flatMap(c -> c.attribute("name"))).orElse(""),
                        x, y, number(bounds.get(), "width"), number(bounds.get(), "height")));
            }
            collectAdded(node.children(), x, y, old, merged, into);
        }
    }

    /** Узлы и рёбра представления по идентификатору, вложенные включительно. */
    private static Map<ArchiId, DocumentNode> diagram(DocumentNode view) {
        Map<ArchiId, DocumentNode> nodes = new LinkedHashMap<>();
        view.children().forEach(child -> walk(child, nodes));
        return nodes;
    }

    private static void walk(DocumentNode node, Map<ArchiId, DocumentNode> into) {
        into.put(node.archiId(), node);
        node.children().forEach(child -> walk(child, into));
    }

    /** Свои атрибуты и значения узла, без вложенных: сдвиг ребёнка не правка родителя. */
    private static List<Object> own(DocumentNode node) {
        List<Object> own = new ArrayList<>(node.attributes().list());
        node.content().stream().filter(item -> !(item instanceof DocumentNode))
                .forEach(item -> own.add(item instanceof DocumentValue value
                        ? Arrays.asList(value.tag(), value.attributes().list(), value.text()) : item));
        return own;
    }

    private static int number(DocumentValue value, String attribute) {
        return value.attribute(attribute).map(v -> {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException notNumber) {
                return 0;
            }
        }).orElse(0);
    }

    private static String bareType(String xsiType) {
        return xsiType.startsWith("archimate:") ? xsiType.substring("archimate:".length()) : xsiType;
    }

    private static InterchangeException noMergeBase(GitSha commit, ModelId model) {
        return new InterchangeException(InterchangeCodes.GIT_NO_MERGE_BASE, Failure.CONFLICT,
                Message.of(InterchangeMessages.GIT_NO_MERGE_BASE, commit.value(), model.value()), Map.of());
    }

    private static InterchangeException rejected(ImportSession session, Message reason, String code) {
        return new InterchangeException(code, Failure.UNPROCESSABLE, reason,
                Map.of("sessionId", session.id().value(), "findings", session.findings()));
    }
}
