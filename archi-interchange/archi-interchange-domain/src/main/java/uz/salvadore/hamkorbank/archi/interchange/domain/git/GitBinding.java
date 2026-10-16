package uz.salvadore.hamkorbank.archi.interchange.domain.git;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.AggregateRoot;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Привязка рабочего пространства к репозиторию — агрегат. Один репозиторий на
 * пространство, каталог на модель. От состояния зависит правило за пределами
 * контекста: снимки версий чистятся только при доступном репозитории.
 */
public final class GitBinding extends AggregateRoot<WorkspaceId> {

    /** Наибольшая длина адреса репозитория. */
    public static final int URL_MAX = 1000;
    /** Наибольшая длина имени ветки. */
    public static final int BRANCH_MAX = 200;
    /** Ветка, если в привязке она не задана. */
    public static final String DEFAULT_BRANCH = "main";
    private static final Pattern URL = Pattern.compile("^(https?|ssh|git|file)://\\S+$|^[\\w.-]+@[\\w.-]+:\\S+$");
    private static final Pattern BRANCH = Pattern.compile("^(?!/)(?!.*//)(?!.*\\.\\.)(?!.*@\\{)(?!.*\\.lock$)"
            + "(?!.*/$)(?!.*\\.$)[^\\s~^:?*\\[\\\\\\x00-\\x1f\\x7f]+$");

    private final String repoUrl;
    private final String branch;
    private final SecretRef tokenRef;
    private final GitBindingStatus status;
    private final GitSha lastCommitSha;
    private final Instant lastCheckedAt;
    private final List<GitAuthor> authors;

    private GitBinding(WorkspaceId workspaceId, String repoUrl, String branch, SecretRef tokenRef,
                       GitBindingStatus status, GitSha lastCommitSha, Instant lastCheckedAt,
                       List<GitAuthor> authors) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(tokenRef, "tokenRef");
        Objects.requireNonNull(status, "status");
        if (repoUrl == null || repoUrl.length() > URL_MAX || !URL.matcher(repoUrl).matches()) {
            throw new InvalidValueException(InterchangeMessages.GIT_URL_INVALID, String.valueOf(repoUrl));
        }
        if (branch == null || branch.length() > BRANCH_MAX || !BRANCH.matcher(branch).matches()) {
            throw new InvalidValueException(InterchangeMessages.GIT_BRANCH_INVALID, String.valueOf(branch));
        }
        authors = List.copyOf(authors);
        Set<String> subjects = new HashSet<>();
        for (GitAuthor author : authors) {
            if (!subjects.add(author.subject())) {
                throw new InvalidValueException(InterchangeMessages.GIT_AUTHOR_REPEATED, author.subject());
            }
        }
        super(workspaceId);
        this.repoUrl = repoUrl;
        this.branch = branch;
        this.tokenRef = tokenRef;
        this.status = status;
        this.lastCommitSha = lastCommitSha;
        this.lastCheckedAt = lastCheckedAt;
        this.authors = authors;
    }

    /**
     * Сборка из сохранённых значений — для хранилища; значения проверяются, как при создании.
     * {@code null} в последнем коммите и моменте проверки — их ещё не было.
     */
    public static GitBinding restore(WorkspaceId workspaceId, String repoUrl, String branch, SecretRef tokenRef,
                                     GitBindingStatus status, GitSha lastCommitSha,
                                     Instant lastCheckedAt, List<GitAuthor> authors) {
        return new GitBinding(workspaceId, repoUrl, branch, tokenRef, status, lastCommitSha, lastCheckedAt, authors);
    }

    /**
     * Новая привязка: {@code CONFIGURED}, коммитов сервера ещё нет. Ветка не задана ({@code null}
     * или пустая) — {@code main}.
     */
    public static GitBinding configure(WorkspaceId workspaceId, String repoUrl, String branch,
                                       SecretRef tokenRef, List<GitAuthor> authors) {
        return new GitBinding(workspaceId, repoUrl, branch == null || branch.isBlank() ? DEFAULT_BRANCH : branch,
                tokenRef, GitBindingStatus.CONFIGURED, null, null, authors);
    }

    /** Можно ли публиковать: снятая привязка — нет. */
    public boolean writable() {
        return status != GitBindingStatus.DISABLED;
    }

    /** Настроена и отвечала при последнем обращении — условие очистки снимков версий. */
    public boolean reachable() {
        return status == GitBindingStatus.CONFIGURED;
    }

    /** Репозиторий ответил. */
    public GitBinding checked(Instant at) {
        requireWritable();
        return new GitBinding(workspaceId(), repoUrl, branch, tokenRef, GitBindingStatus.CONFIGURED, lastCommitSha,
                Objects.requireNonNull(at, "at"), authors);
    }

    /** Репозиторий не ответил или отказал в доступе. */
    public GitBinding unreachable(Instant at) {
        requireWritable();
        return new GitBinding(workspaceId(), repoUrl, branch, tokenRef, GitBindingStatus.UNREACHABLE, lastCommitSha,
                Objects.requireNonNull(at, "at"), authors);
    }

    /** Сервер довёл ветку до этого коммита — своего или принятого поверх чужого. */
    public GitBinding committed(GitSha sha, Instant at) {
        return checked(at).withHead(sha);
    }

    /** Снять привязку. Коммиты в репозитории не трогаются. */
    public GitBinding disabled() {
        requireWritable();
        return new GitBinding(workspaceId(), repoUrl, branch, tokenRef, GitBindingStatus.DISABLED, lastCommitSha,
                lastCheckedAt, authors);
    }

    /** Автор коммитов для данного субъекта Keycloak, если он есть в привязке. */
    public Optional<GitAuthor> authorOf(String subject) {
        return authors.stream().filter(a -> a.subject().equals(subject)).findFirst();
    }

    private GitBinding withHead(GitSha sha) {
        return new GitBinding(workspaceId(), repoUrl, branch, tokenRef, status, Objects.requireNonNull(sha, "sha"),
                lastCheckedAt, authors);
    }

    private void requireWritable() {
        if (!writable()) {
            throw new InterchangeDomainException(InterchangeCodes.GIT_BINDING_DISABLED,
                    Message.of(InterchangeMessages.GIT_BINDING_DISABLED, workspaceId().value()));
        }
    }

    /** Пространство, которое привязано к репозиторию. */
    public WorkspaceId workspaceId() {
        return id();
    }

    /** Адрес репозитория. */
    public String repoUrl() {
        return repoUrl;
    }

    /** Ветка, в которую публикуются версии. */
    public String branch() {
        return branch;
    }

    /** Ссылка на секрет с токеном доступа — сам токен здесь не хранится. */
    public SecretRef tokenRef() {
        return tokenRef;
    }

    /** Состояние привязки: доступен ли репозиторий. */
    public GitBindingStatus status() {
        return status;
    }

    /**
     * Последний коммит, сделанный сервером в ветке: от него отсчитывается, что в ветке появилось не от нас.
     * {@code null} — сервер ещё не коммитил.
     */
    public GitSha lastCommitSha() {
        return lastCommitSha;
    }

    /** Момент последней проверки репозитория; {@code null} — ещё не проверялся. */
    public Instant lastCheckedAt() {
        return lastCheckedAt;
    }

    /** Авторы коммитов, сопоставленные с пользователями приложения. */
    public List<GitAuthor> authors() {
        return authors;
    }
}
