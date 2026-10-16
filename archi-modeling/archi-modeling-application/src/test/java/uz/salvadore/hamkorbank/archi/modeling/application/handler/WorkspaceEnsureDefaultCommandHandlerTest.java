package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EnsureDefaultWorkspaceCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.WorkspaceResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.application.port.WorkspaceRepository;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.Workspace;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

class WorkspaceEnsureDefaultCommandHandlerTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    private final List<Workspace> stored = new ArrayList<>();
    private final WorkspaceEnsureDefaultCommandHandler handler = new WorkspaceEnsureDefaultCommandHandler(
            repository(), direct(), catalog(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Пустая база: имя первого пространства — текст ресурса, а не строка в конфигурации")
    void defaultNameComesFromCatalog() {
        WorkspaceResponse created = handler.handle(new EnsureDefaultWorkspaceCommand(null));

        assertEquals("текст:" + ModelingMessages.DEFAULT_WORKSPACE_NAME, created.name());
        assertEquals(1, stored.size());
        assertEquals(created.id(), stored.getFirst().id().value());
    }

    @Test
    @DisplayName("Имя, заданное установкой, сильнее текста ресурса")
    void configuredNameWins() {
        assertEquals("Хамкорбанк", handler.handle(new EnsureDefaultWorkspaceCommand("Хамкорбанк")).name());
    }

    @Test
    @DisplayName("Пространство уже есть — новое не заводится, имя не меняется")
    void existingWorkspaceIsKept() {
        Workspace existing = Workspace.restore(WorkspaceId.of(IDS.generate()), "Архитектура", NOW);
        stored.add(existing);

        assertEquals(new WorkspaceResponse(existing.id().value(), "Архитектура"),
                handler.handle(new EnsureDefaultWorkspaceCommand("Другое")));
        assertEquals(List.of(existing), stored);
    }

    private WorkspaceRepository repository() {
        return new WorkspaceRepository() {
            @Override
            public List<Workspace> findAll() {
                return List.copyOf(stored);
            }

            @Override
            public Optional<Workspace> first() {
                return stored.stream().findFirst();
            }

            @Override
            public Optional<Workspace> find(WorkspaceId id) {
                return stored.stream().filter(w -> w.id().equals(id)).findFirst();
            }

            @Override
            public void insert(Workspace workspace) {
                stored.add(workspace);
            }
        };
    }

    private static UnitOfWork direct() {
        return new UnitOfWork() {
            @Override
            public <T> T write(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T read(Supplier<T> work) {
                return work.get();
            }
        };
    }

    /** Подпись тестового каталога — ключ сообщения: так видно, какой ключ запрошен. */
    private static TextCatalog catalog() {
        return message -> "текст:" + message.key();
    }
}
