package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.application.port.InterchangeMetrics;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitAuthor;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.port.UnitOfWork;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Настройка привязки рабочего пространства к репозиторию — только {@code ADMIN}.
 * Доступность репозитория здесь не проверяется: настройка не ждёт сети, первую
 * проверку делает публикация.
 */
public final class GitBindingService {

    private final GitBindingRepository bindings;
    private final UnitOfWork unitOfWork;
    private final InterchangeMetrics metrics;
    private final Clock clock;

    public GitBindingService(GitBindingRepository bindings, UnitOfWork unitOfWork, InterchangeMetrics metrics,
                             Clock clock) {
        this.bindings = bindings;
        this.unitOfWork = unitOfWork;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Привязка пространства к репозиторию; нет привязки — «не найдено». */
    public GitBinding binding(EditorIdentity actor, UUID workspaceId) {
        InterchangeOperation.CONFIGURE_GIT_BINDING.require(actor);
        return unitOfWork.read(() -> require(new WorkspaceId(workspaceId)));
    }

    /**
     * Новая привязка заменяет прежнюю. Тот же адрес и ветка — последний коммит
     * сервера сохраняется: по нему видно, не переписана ли история.
     */
    public GitBinding configure(EditorIdentity actor, UUID workspaceId, GitBindingSettings settings) {
        InterchangeOperation operation = InterchangeOperation.CONFIGURE_GIT_BINDING;
        return metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            WorkspaceId workspace = new WorkspaceId(workspaceId);
            GitBinding configured;
            try {
                configured = GitBinding.configure(workspace, settings.repoUrl(), settings.branch(),
                        SecretRef.of(String.valueOf(settings.tokenRef())), settings.authors().stream()
                                .map(a -> new GitAuthor(a.subject(), a.name(), a.email())).toList());
            } catch (InvalidValueException e) {
                throw new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE, e.reason(),
                        Map.of());
            }
            return unitOfWork.write(() -> {
                if (!bindings.workspaceExists(workspace)) {
                    throw new InterchangeException(InterchangeCodes.NOT_FOUND, Failure.NOT_FOUND,
                            Message.of(InterchangeMessages.WORKSPACE_NOT_FOUND, workspace), Map.of());
                }
                GitBinding binding = bindings.find(workspace)
                        .filter(old -> old.repoUrl().equals(configured.repoUrl())
                                && old.branch().equals(configured.branch()))
                        .map(GitBinding::lastCommitSha)
                        .map(sha -> configured.committed(sha, clock.instant()))
                        .orElse(configured);
                bindings.save(binding);
                return binding;
            });
        });
    }

    /** Снять привязку: публикация и очистка снимков в пространстве прекращаются, коммиты остаются. */
    public void disable(EditorIdentity actor, UUID workspaceId) {
        InterchangeOperation operation = InterchangeOperation.CONFIGURE_GIT_BINDING;
        metrics.observe(operation.useCase(), () -> {
            operation.require(actor);
            unitOfWork.write(() -> {
                GitBinding binding = require(new WorkspaceId(workspaceId));
                if (binding.writable()) {
                    bindings.save(binding.disabled());
                }
                return null;
            });
            return null;
        });
    }

    private GitBinding require(WorkspaceId workspace) {
        return bindings.find(workspace).orElseThrow(() -> new InterchangeException(InterchangeCodes.NOT_FOUND,
                Failure.NOT_FOUND, Message.of(InterchangeMessages.GIT_BINDING_MISSING, workspace), Map.of()));
    }
}
