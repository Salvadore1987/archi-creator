package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.DisableGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.MergeModelBranchCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PullModelFromGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.PushModelToGitCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetGitBindingQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitMergeQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.PreviewGitPullQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitBindingResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitBindingConfigureCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitBindingDisableCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitBindingGetQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.InterchangeKernel;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelBranchMergeCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelBranchMergePreviewQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelGitPullCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelGitPullPreviewQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelGitPushCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitSha;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

/**
 * Сценарии Git на входе: роль проверяется до любой работы — шаги сценария даже не вызываются,
 * — и настройка привязки, у которой шагов вне handler'а нет.
 */
class GitHandlersTest {

    private static final EditorIdentity ADMIN = EditorIdentity.of("admin-git", Role.ADMIN);
    private static final EditorIdentity ARCHITECT = EditorIdentity.of("architect-git", Role.ARCHITECT);
    private static final EditorIdentity VIEWER = EditorIdentity.of("viewer-git", Role.VIEWER);
    private static final UUID WORKSPACE = UUIDUtil.uuid("0192f000-0000-7000-8000-000000000001");
    private static final UUID MODEL = UUIDUtil.uuid("0192f000-0000-7000-8000-000000000002");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T09:00:00Z"), ZoneOffset.UTC);

    private final GitFakes.MemoryBindings bindings = new GitFakes.MemoryBindings();
    private final InterchangeKernel kernel = new InterchangeKernel(new GitFakes.RollbackUnitOfWork(List.of(bindings)),
            events -> { }, InterchangeMetrics.NONE, message -> message.key() + message.args(), CLOCK);

    @Test
    @DisplayName("FR-37: без роли импорт по ссылке, слияние и публикация отказывают до сети и чтения модели")
    void scenariosRequireRoleBeforeWork() {
        assertDenied(() -> new ModelGitPullPreviewQueryHandler(kernel, null)
                .handle(VIEWER, new PreviewGitPullQuery(MODEL, "main")));
        assertDenied(() -> new ModelGitPullCommandHandler(kernel, null)
                .handle(VIEWER, new PullModelFromGitCommand(MODEL, "main", null, null, null)));
        assertDenied(() -> new ModelBranchMergePreviewQueryHandler(kernel, null)
                .handle(VIEWER, new PreviewGitMergeQuery(MODEL, "proposal", Map.of())));
        assertDenied(() -> new ModelBranchMergeCommandHandler(kernel, null)
                .handle(VIEWER, new MergeModelBranchCommand(MODEL, "proposal", null, null, null, Map.of(), null)));
        assertDenied(() -> new ModelGitPushCommandHandler(kernel, null)
                .handle(VIEWER, new PushModelToGitCommand(MODEL)));
    }

    @Test
    @DisplayName("FR-37: привязку читает, настраивает и снимает только ADMIN")
    void bindingIsAdminOnly() {
        assertDenied(() -> new GitBindingGetQueryHandler(kernel, bindings)
                .handle(ARCHITECT, new GetGitBindingQuery(WORKSPACE)));
        assertDenied(() -> configure(false).handle(ARCHITECT, settings("https://git.example.com/a.git")));
        assertDenied(() -> new GitBindingDisableCommandHandler(kernel, bindings)
                .handle(ARCHITECT, new DisableGitBindingCommand(WORKSPACE)));
    }

    @Test
    @DisplayName("локальный репозиторий по file:// без флага конфигурации не привязывается")
    void localRepositoryIsRefusedByDefault() {
        InterchangeException e = assertThrows(InterchangeException.class,
                () -> configure(false).handle(ADMIN, settings("file:///srv/secrets")));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.GIT_URL_LOCAL, e.reason().key());
    }

    @Test
    @DisplayName("с флагом конфигурации локальный репозиторий привязывается — для тестов")
    void localRepositoryIsAllowedByFlag() {
        assertEquals("file:///tmp/landscape.git",
                configure(true).handle(ADMIN, settings("file:///tmp/landscape.git")).repoUrl());
    }

    @Test
    @DisplayName("сетевой адрес привязывается без флага; ответ — без токена, со ссылкой на секрет")
    void networkRepositoryNeedsNoFlag() {
        GitBindingResponse binding = configure(false).handle(ADMIN, settings("https://git.example.com/ea/l.git"));

        assertEquals("https://git.example.com/ea/l.git", binding.repoUrl());
        assertEquals("env:ARCHI_GIT_TOKEN", binding.tokenRef());
        assertEquals(binding, new GitBindingGetQueryHandler(kernel, bindings)
                .handle(ADMIN, new GetGitBindingQuery(WORKSPACE)));
    }

    @Test
    @DisplayName("ссылка на секрет не задана — «не задано», а не формат значения «null»")
    void missingTokenRefIsMissing() {
        InterchangeException e = assertThrows(InterchangeException.class, () -> configure(false).handle(ADMIN,
                new ConfigureGitBindingCommand(WORKSPACE, "https://git.example.com/a.git", null, null, List.of())));

        assertEquals(InterchangeMessages.GIT_PULL_FIELD_MISSING, e.reason().key());
    }

    @Test
    @DisplayName("перенастройка на тот же адрес сохраняет последний коммит, но не отмечает проверку доступности")
    void reconfigurationKeepsHeadWithoutCheckMark() {
        GitSha head = GitSha.of("a".repeat(40));
        bindings.save(GitBinding.configure(new WorkspaceId(WORKSPACE), "https://git.example.com/a.git", null,
                SecretRef.of("env:ARCHI_GIT_TOKEN"), List.of()).committed(head, CLOCK.instant()));

        GitBindingResponse again = configure(false).handle(ADMIN, settings("https://git.example.com/a.git"));

        assertEquals(head.value(), again.lastCommitSha());
        assertNull(again.lastCheckedAt());
    }

    private GitBindingConfigureCommandHandler configure(boolean localRepositories) {
        return new GitBindingConfigureCommandHandler(kernel, bindings, localRepositories);
    }

    private static ConfigureGitBindingCommand settings(String url) {
        return new ConfigureGitBindingCommand(WORKSPACE, url, null, "env:ARCHI_GIT_TOKEN", List.of());
    }

    private static void assertDenied(Executable call) {
        assertEquals(InterchangeCodes.ACCESS_DENIED, assertThrows(InterchangeException.class, call).code());
    }
}
