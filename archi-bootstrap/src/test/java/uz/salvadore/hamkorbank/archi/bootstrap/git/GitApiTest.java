package uz.salvadore.hamkorbank.archi.bootstrap.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.fasterxml.uuid.impl.UUIDUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
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
                null, "env:ARCHI_GIT_TOKEN_IT",
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

    protected void rename(UUID model, String from, String to) throws Exception {
        List<String> ids = read(mvc.perform(get("/api/v1/models/" + model).with(architect())).andReturn(),
                "$.elements[?(@.name == '" + from + "')].id");
        mvc.perform(patch("/api/v1/elements/" + ids.getFirst()).with(architect())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + to + "\"}"))
                .andExpect(status().isOk());
    }

    protected void lock(UUID model) throws Exception {
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());
    }

    protected List<Long> versions(UUID model) {
        return column("select version_no from model_version where model_id = ? order by version_no", Long.class,
                model);
    }


    /** Правка YAML модели не через сервер: коммит в ветку {@code branch} поверх головы {@code main}. */
    protected RevCommit externalCommit(String branch, UnaryOperator<String> edit) throws Exception {
        Path work = Files.createTempDirectory(temp, "work");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(work.toFile()).call()) {
            if (!branch.equals("main")) {
                git.checkout().setCreateBranch(true).setName(branch).call();
            }
            boolean changed = false;
            try (Stream<Path> files = Files.walk(work.resolve("models"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".yaml")).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String edited = edit.apply(text);
                    if (!edited.equals(text)) {
                        Files.writeString(file, edited, StandardCharsets.UTF_8);
                        changed = true;
                    }
                }
            }
            assertThat(changed).as("правка нашла, что менять").isTrue();
            git.add().addFilepattern("models").call();
            RevCommit commit = git.commit().setMessage("правка YAML руками")
                    .setAuthor(new PersonIdent("Ревьюер", "review@bank.uz"))
                    .setCommitter(new PersonIdent("Ревьюер", "review@bank.uz")).call();
            git.push().setRemote("origin").add(branch).call();
            return commit;
        }
    }
}
