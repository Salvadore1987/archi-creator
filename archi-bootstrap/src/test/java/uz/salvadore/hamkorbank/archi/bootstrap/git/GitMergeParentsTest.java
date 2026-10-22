package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.uuid.Generators;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.CommitRequest;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Person;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Pushed;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Remote;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Общий предок и второй родитель коммита на настоящем JGit: ветка предложения, слитая
 * в модель, становится предком ветки привязки без текстового слияния.
 */
class GitMergeParentsTest {

    @TempDir
    Path temp;

    private Path bare;
    private GitRepositoryPort repository;
    private Remote remote;
    private RevCommit first;
    private RevCommit second;
    private RevCommit proposal;

    @BeforeEach
    void setUp() throws Exception {
        bare = temp.resolve("remote.git");
        Git.init().setBare(true).setDirectory(bare.toFile()).setInitialBranch("main").call().close();
        Path work = temp.resolve("work");
        try (Git git = Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(work.toFile()).call()) {
            RefUpdate head = git.getRepository().updateRef(Constants.HEAD);
            head.link(Constants.R_HEADS + "main");
            first = commit(git, work, "first");
            git.checkout().setCreateBranch(true).setName("proposal/kanaly").call();
            proposal = commit(git, work, "proposal");
            git.checkout().setName("main").call();
            second = commit(git, work, "second");
            git.push().setRemote("origin").setPushAll().call();
        }
        repository = new JGitRepository(temp.resolve("cache"), "oauth2", Duration.ofSeconds(10));
        remote = new Remote(new WorkspaceId(Generators.timeBasedEpochGenerator().generate()), bare.toUri().toString(),
                "main", null);
    }

    @Test
    @DisplayName("FR-39: общий предок ветки предложения и ветки привязки — точка развилки")
    void mergeBaseIsForkPoint() {
        repository.fetch(remote);

        assertThat(repository.mergeBase(remote, sha(second), sha(proposal))).contains(sha(first));
        assertThat(repository.mergeBase(remote, sha(first), sha(second))).contains(sha(first));
        assertThat(repository.mergeBase(remote, sha(second),
                GitSha.of("0123456789abcdef0123456789abcdef01234567"))).isEmpty();
    }

    @Test
    @DisplayName("FR-39, INV-IXC-009: слитый коммит — второй родитель; ветка предложения становится предком")
    void mergedCommitBecomesSecondParent() throws Exception {
        GitSha head = repository.fetch(remote).orElse(null);

        Pushed pushed = (Pushed) repository.push(remote, head, request("merged", sha(proposal)));

        RevCommit commit = parse(pushed.sha());
        assertThat(commit.getParents()).extracting(ObjectId::name).containsExactly(second.name(), proposal.name());
        assertThat(repository.contains(remote, sha(proposal), pushed.sha())).isTrue();
    }

    @Test
    @DisplayName("FR-39: слитый коммит уже в истории — второго родителя нет; без правок дерева коммита нет")
    void mergedAncestorAddsNoParent() throws Exception {
        GitSha head = repository.fetch(remote).orElse(null);

        Pushed pushed = (Pushed) repository.push(remote, head, request("x", sha(first)));
        assertThat(parse(pushed.sha()).getParents()).extracting(ObjectId::name).containsExactly(second.name());

        assertThat(repository.push(remote, pushed.sha(), request("x", sha(first))))
                .isInstanceOf(GitRepositoryPort.Unchanged.class);
    }

    @Test
    @DisplayName("FR-39: принятие слиянием без правок дерева — всё равно коммит, чтобы ветка предложения вошла")
    void mergeWithoutTreeChangeStillCommits() throws Exception {
        GitSha head = repository.fetch(remote).orElse(null);
        Pushed base = (Pushed) repository.push(remote, head, request("same", null));

        Pushed merged = (Pushed) repository.push(remote, base.sha(), request("same", sha(proposal)));

        assertThat(parse(merged.sha()).getParents()).hasSize(2);
    }

    private CommitRequest request(String content, GitSha merged) {
        Person person = new Person("t", "t@t", Instant.parse("2026-10-14T10:00:00Z"));
        return new CommitRequest("models/x", new TreeMap<>(Map.of("a.yaml", content.getBytes(StandardCharsets.UTF_8))),
                new TreeMap<>(), "msg", person, person, merged);
    }

    private RevCommit parse(GitSha sha) throws Exception {
        try (Repository repo = new FileRepositoryBuilder().setGitDir(bare.toFile()).setBare().build();
                RevWalk walk = new RevWalk(repo)) {
            return walk.parseCommit(ObjectId.fromString(sha.value()));
        }
    }

    private static GitSha sha(RevCommit commit) {
        return GitSha.of(commit.name());
    }

    private static RevCommit commit(Git git, Path work, String text) throws Exception {
        Files.writeString(work.resolve(text + ".md"), text + "\n");
        git.add().addFilepattern(text + ".md").call();
        PersonIdent person = new PersonIdent("Тест", "test@bank.uz");
        return git.commit().setMessage(text).setAuthor(person).setCommitter(person).call();
    }
}
