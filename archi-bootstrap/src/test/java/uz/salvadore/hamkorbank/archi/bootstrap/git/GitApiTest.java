package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.impl.UUIDUtil;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitBindingService;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitBindingSettings;
import uz.salvadore.hamkorbank.archi.interchange.application.git.GitPublishingService;

/**
 * Основа интеграционных тестов выгрузки в Git: настоящая база, настоящий JGit
 * и bare-репозиторий во временном каталоге по {@code file://}. Чужие записи
 * outbox прошлых тестов закрываются заранее: публикуется только своё.
 */
abstract class GitApiTest extends ApiTest {

    protected static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @TempDir
    protected Path temp;

    @Autowired
    protected GitBindingService bindings;
    @Autowired
    protected GitPublishingService publishing;

    protected Path remote;

    @BeforeEach
    void bindRepository() throws Exception {
        jdbc.update("update outbox_event set status = 'SKIPPED' where status in ('PENDING', 'DIVERGED')");
        remote = temp.resolve("landscape.git");
        Git.init().setBare(true).setDirectory(remote.toFile()).setInitialBranch("main").call().close();
        bindings.configure(ADMIN, workspace().value(), new GitBindingSettings(remote.toUri().toString(),
                Optional.empty(), "env:ARCHI_GIT_TOKEN_IT",
                List.of(new GitBindingSettings.Author("architect-api", "Архитектор", "architect@bank.uz"))));
    }

    @AfterEach
    void unbindRepository() {
        jdbc.update("delete from git_binding");
        jdbc.update("update outbox_event set status = 'SKIPPED' where status in ('PENDING', 'DIVERGED')");
    }

    protected UUID importModel(byte[] xml) throws Exception {
        return UUIDUtil.uuid(ApiTest.<String>read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "model.archimate", "application/xml", xml))
                        .header("Idempotency-Key", "git-" + IDS.generate()).with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId"));
    }

    protected void save(UUID model, String comment) throws Exception {
        mvc.perform(post("/api/v1/models/" + model + "/versions").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"" + comment + "\"}"))
                .andExpect(status().isCreated());
    }

    protected Optional<String> gitSha(UUID model, long versionNo) {
        return column("select git_sha from model_version where model_id = ? and version_no = ?", String.class,
                model, versionNo).stream().filter(java.util.Objects::nonNull).findFirst();
    }

    protected List<RevCommit> history() throws Exception {
        try (Git git = Git.open(remote.toFile())) {
            List<RevCommit> commits = new ArrayList<>();
            git.log().add(git.getRepository().resolve("refs/heads/main")).call().forEach(commits::add);
            return commits;
        }
    }

    /** Файлы коммита с путями от корня. */
    protected SortedMap<String, byte[]> tree(RevCommit commit) throws Exception {
        SortedMap<String, byte[]> files = new TreeMap<>();
        try (Git git = Git.open(remote.toFile()); RevWalk walk = new RevWalk(git.getRepository());
             TreeWalk tree = new TreeWalk(git.getRepository())) {
            tree.addTree(walk.parseCommit(commit).getTree());
            tree.setRecursive(true);
            while (tree.next()) {
                files.put(tree.getPathString(), git.getRepository().open(tree.getObjectId(0)).getBytes());
            }
        }
        return files;
    }

    /** Файлы каталога модели в коммите — пути относительно каталога. */
    protected SortedMap<String, byte[]> modelDirectory(RevCommit commit) throws Exception {
        SortedMap<String, byte[]> all = tree(commit);
        String directory = all.keySet().stream().filter(p -> p.startsWith("models/") && p.endsWith("/model.yaml"))
                .findFirst().orElseThrow().replace("model.yaml", "");
        SortedMap<String, byte[]> files = new TreeMap<>();
        all.forEach((path, bytes) -> {
            if (path.startsWith(directory)) {
                files.put(path.substring(directory.length()), bytes);
            }
        });
        return files;
    }
}
