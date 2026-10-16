package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.uuid.Generators;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitRepositoryPort.Remote;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.RefKind;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.ResolvedRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;

/**
 * Ссылка в коммит на настоящем JGit: ветка, тег (лёгкий и аннотированный), полное имя,
 * коммит полным и сокращённым идентификатором; тег раньше одноимённой ветки.
 */
class GitRefResolutionTest {

    @TempDir
    Path temp;

    private GitRepositoryPort repository;
    private Remote remote;
    private RevCommit first;
    private RevCommit second;
    private RevCommit feature;

    @BeforeEach
    void setUp() throws Exception {
        Path bare = temp.resolve("remote.git");
        Git.init().setBare(true).setDirectory(bare.toFile()).setInitialBranch("main").call().close();
        Path work = temp.resolve("work");
        try (Git git = Git.cloneRepository().setURI(bare.toUri().toString()).setDirectory(work.toFile()).call()) {
            RefUpdate head = git.getRepository().updateRef(Constants.HEAD);
            head.link(Constants.R_HEADS + "main");
            first = commit(git, work, "first");
            git.tag().setName("v1").setAnnotated(true).setMessage("релиз").setTagger(person()).call();
            second = commit(git, work, "second");
            git.tag().setName("light").setAnnotated(false).call();
            git.branchCreate().setName("release").setStartPoint(first).call();
            git.tag().setName("release").setObjectId(git.getRepository().parseCommit(second)).setAnnotated(false)
                    .call();
            git.checkout().setCreateBranch(true).setName("feature").call();
            feature = commit(git, work, "feature");
            git.push().setRemote("origin").setPushAll().setPushTags().call();
        }
        repository = new JGitRepository(temp.resolve("cache"), "oauth2", Duration.ofSeconds(10));
        remote = new Remote(new WorkspaceId(Generators.timeBasedEpochGenerator().generate()), bare.toUri().toString(),
                "main", null);
    }

    @Test
    @DisplayName("FR-38: ветка — голова ветки, вид BRANCH; полное имя refs/heads/… — то же")
    void branchResolvesToItsHead() {
        assertThat(resolve("main")).contains(resolved("main", RefKind.BRANCH, second));
        assertThat(resolve("feature")).contains(resolved("feature", RefKind.BRANCH, feature));
        assertThat(resolve("refs/heads/feature")).contains(resolved("refs/heads/feature", RefKind.BRANCH, feature));
    }

    @Test
    @DisplayName("FR-38: тег — коммит под ним, аннотированный снимается до коммита")
    void tagResolvesToTaggedCommit() {
        assertThat(resolve("v1")).contains(resolved("v1", RefKind.TAG, first));
        assertThat(resolve("light")).contains(resolved("light", RefKind.TAG, second));
        assertThat(resolve("refs/tags/v1")).contains(resolved("refs/tags/v1", RefKind.TAG, first));
    }

    @Test
    @DisplayName("FR-38: тег раньше одноимённой ветки — как в git rev-parse")
    void tagWinsOverBranchOfSameName() {
        assertThat(resolve("release")).contains(resolved("release", RefKind.TAG, second));
        assertThat(resolve("refs/heads/release")).map(ResolvedRef::sha).contains(sha(first));
    }

    @Test
    @DisplayName("FR-38: коммит — полным идентификатором и сокращённым от 7 знаков")
    void commitResolvesByFullAndAbbreviatedSha() {
        assertThat(resolve(first.name())).contains(resolved(first.name(), RefKind.COMMIT, first));
        String abbreviated = feature.name().substring(0, 7);
        assertThat(resolve(abbreviated)).contains(resolved(abbreviated, RefKind.COMMIT, feature));
    }

    @Test
    @DisplayName("FR-38: неизвестная ссылка и чужой коммит — пусто, недопустимое имя — отказ")
    void unknownReferenceIsEmpty() {
        assertThat(resolve("nope")).isEmpty();
        assertThat(resolve("0123456789abcdef0123456789abcdef01234567")).isEmpty();
        assertThatThrownBy(() -> GitRef.of("main..feature")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> GitRef.of("")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> GitRef.of("-upload-pack")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("FR-38: недоступный репозиторий — Unreachable, а не «не найдено»")
    void unreachableRepositoryIsReported() {
        Remote missing = new Remote(remote.workspace(), temp.resolve("missing.git").toUri().toString(), "main",
                null);

        assertThatThrownBy(() -> repository.resolve(missing, GitRef.of("main")))
                .isInstanceOf(GitRepositoryPort.Unreachable.class);
    }

    private Optional<ResolvedRef> resolve(String ref) {
        return repository.resolve(remote, GitRef.of(ref));
    }

    private static ResolvedRef resolved(String ref, RefKind kind, RevCommit commit) {
        return new ResolvedRef(GitRef.of(ref), kind, sha(commit));
    }

    private static GitSha sha(RevCommit commit) {
        return GitSha.of(commit.name());
    }

    private static RevCommit commit(Git git, Path work, String text) throws Exception {
        Files.writeString(work.resolve("README.md"), text + "\n");
        git.add().addFilepattern("README.md").call();
        return git.commit().setMessage(text).setAuthor(person()).setCommitter(person()).call();
    }

    private static PersonIdent person() {
        return new PersonIdent("Тест", "test@bank.uz");
    }
}
