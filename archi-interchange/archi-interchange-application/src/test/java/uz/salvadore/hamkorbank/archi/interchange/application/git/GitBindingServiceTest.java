package uz.salvadore.hamkorbank.archi.interchange.application.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Role;

class GitBindingServiceTest {

    private static final EditorIdentity ADMIN = EditorIdentity.of("admin-git", Role.ADMIN);
    private static final UUID WORKSPACE = UUIDUtil.uuid("0192f000-0000-7000-8000-000000000001");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T09:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("локальный репозиторий по file:// без флага конфигурации не привязывается")
    void localRepositoryIsRefusedByDefault() {
        InterchangeException e = assertThrows(InterchangeException.class,
                () -> service(false).configure(ADMIN, WORKSPACE, settings("file:///srv/secrets")));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.GIT_URL_LOCAL, e.reason().key());
    }

    @Test
    @DisplayName("с флагом конфигурации локальный репозиторий привязывается — для тестов")
    void localRepositoryIsAllowedByFlag() {
        assertEquals("file:///tmp/landscape.git",
                service(true).configure(ADMIN, WORKSPACE, settings("file:///tmp/landscape.git")).repoUrl());
    }

    @Test
    @DisplayName("сетевой адрес привязывается без флага")
    void networkRepositoryNeedsNoFlag() {
        assertEquals("https://git.example.com/ea/landscape.git", service(false)
                .configure(ADMIN, WORKSPACE, settings("https://git.example.com/ea/landscape.git")).repoUrl());
    }

    private static GitBindingService service(boolean localRepositories) {
        GitFakes.MemoryBindings bindings = new GitFakes.MemoryBindings();
        return new GitBindingService(bindings, new GitFakes.RollbackUnitOfWork(List.of(bindings)),
                InterchangeMetrics.NONE, CLOCK, localRepositories);
    }

    private static GitBindingSettings settings(String url) {
        return new GitBindingSettings(url, null, "env:ARCHI_GIT_TOKEN", List.of());
    }
}
