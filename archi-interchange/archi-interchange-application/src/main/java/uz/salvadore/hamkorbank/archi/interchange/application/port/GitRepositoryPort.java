package uz.salvadore.hamkorbank.archi.interchange.application.port;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ResolvedRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Репозиторий рабочего пространства. Ветка двигается только вперёд: push проверяет,
 * что голова ветки та, от которой строился коммит, и без принуждения — ни force push,
 * ни rebase, ни удаления веток через этот порт сделать нельзя.
 *
 * <p>Сетевой отказ — {@link Unreachable}: вызывающий откладывает работу, а не теряет её.
 */
public interface GitRepositoryPort {

    /** Свежая голова ветки; ветки ещё нет — пусто. */
    Optional<GitSha> fetch(Remote remote);

    /**
     * Ссылка в коммит — как в {@code git rev-parse}: полное имя {@code refs/…}, тег, ветка,
     * затем идентификатор коммита, полный или сокращённый. Тег раньше одноимённой ветки.
     * Перед разрешением забирает из репозитория все ветки и теги; найденный коммит потом
     * читается {@link #read}. Не нашлось или сокращение неоднозначно — пусто.
     */
    Optional<ResolvedRef> resolve(Remote remote, GitRef ref);

    /** Файлы каталога в коммите — пути относительно каталога; каталога нет — пусто. */
    SortedMap<String, byte[]> read(Remote remote, GitSha commit, String directory);

    /** Один файл коммита по пути от корня; нет — пусто. */
    Optional<byte[]> file(Remote remote, GitSha commit, String path);

    /** Имена подкаталогов в коммите. */
    List<String> directories(Remote remote, GitSha commit, String parent);

    /** Достижим ли {@code ancestor} из {@code head}: история не переписана. */
    boolean contains(Remote remote, GitSha ancestor, GitSha head);

    /**
     * Общий предок двух коммитов — ближайший, как {@code git merge-base}. Коммита нет в кэше
     * и после забора всех веток или общего предка нет — пусто.
     */
    Optional<GitSha> mergeBase(Remote remote, GitSha first, GitSha second);

    /** Последний коммит из истории {@code head}, менявший каталог; не менял никто — пусто. */
    Optional<Commit> lastChange(Remote remote, GitSha head, String directory);

    /** Коммит из истории {@code head} с этими трейлерами — повтор после сбоя не дублирует коммит. */
    Optional<GitSha> findByTrailers(Remote remote, GitSha head, Map<String, String> trailers);

    /**
     * Коммит поверх {@code parent}: каталог {@code directory} заменяется файлами целиком,
     * корневые файлы — ставятся, если их ещё нет или они другие. Слитый коммит запроса,
     * которого ещё нет в истории {@code parent}, — второй родитель. Дерево не изменилось
     * и второго родителя нет — {@link Unchanged}, коммита нет. Ветка ушла вперёд, пока
     * строился коммит, — {@link Rejected}; принуждения нет. {@code parent} — {@code null}, когда
     * ветки ещё нет.
     */
    PushResult push(Remote remote, GitSha parent, CommitRequest request);

    /**
     * Адрес, ветка и учётные данные — токен живёт только на время вызова.
     *
     * @param token токен доступа; {@code null} — без учётных данных
     */
    record Remote(WorkspaceId workspace, String url, String branch, String token) {

        public Remote {
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(branch, "branch");
        }

        @Override
        public String toString() {
            return url + "#" + branch;
        }
    }

    /** Автор или коммиттер коммита и время подписи. */
    record Person(String name, String email, Instant when) {
    }

    /** Коммит ветки и его трейлеры по имени. */
    record Commit(GitSha sha, Map<String, String> trailers) {

        public Commit {
            trailers = Map.copyOf(trailers);
        }
    }

    /**
     * @param merged коммит, слитый в модель по сущностям, — кандидат во второго родителя;
     *               {@code null} — слияния не было
     */
    record CommitRequest(String directory, SortedMap<String, byte[]> files, SortedMap<String, byte[]> rootFiles,
                         String message, Person author, Person committer, GitSha merged) {

        public CommitRequest {
            files = copy(files);
            rootFiles = copy(rootFiles);
        }

        public CommitRequest(String directory, SortedMap<String, byte[]> files, SortedMap<String, byte[]> rootFiles,
                             String message, Person author, Person committer) {
            this(directory, files, rootFiles, message, author, committer, null);
        }

        private static SortedMap<String, byte[]> copy(SortedMap<String, byte[]> files) {
            SortedMap<String, byte[]> copy = new TreeMap<>();
            files.forEach((path, bytes) -> copy.put(path, Arrays.copyOf(bytes, bytes.length)));
            return copy;
        }
    }

    /** Итог отправки коммита в ветку. */
    sealed interface PushResult permits Pushed, Unchanged, Rejected {
    }

    /** Коммит в ветке; {@code changedFiles} — сколько путей он тронул. */
    record Pushed(GitSha sha, int changedFiles) implements PushResult {
    }

    /** Содержимое уже в ветке: коммит не нужен, голова та же. */
    record Unchanged(GitSha head) implements PushResult {
    }

    /** Ветка ушла вперёд между чтением и записью — повторить с новой головы. */
    record Rejected() implements PushResult {
    }

    /** Репозиторий не ответил, отказал в доступе или в записи. */
    final class Unreachable extends RuntimeException {

        public Unreachable(String reason, Throwable cause) {
            super(reason, cause);
        }
    }
}
