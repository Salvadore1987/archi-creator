package uz.salvadore.hamkorbank.archi.interchange.application.handler;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeOperation;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitBindingResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.mapper.GitDataMapper;
import uz.salvadore.hamkorbank.archi.interchange.application.port.GitBindingRepository;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitAuthor;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.GitBinding;
import uz.salvadore.hamkorbank.archi.interchange.domain.git.SecretRef;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Настройка привязки рабочего пространства к репозиторию — только {@code ADMIN}. Доступность
 * репозитория здесь не проверяется: настройка не ждёт сети, первую проверку делает публикация.
 */
public final class GitBindingConfigureCommandHandler {

    private final InterchangeKernel kernel;
    private final GitBindingRepository bindings;
    private final boolean localRepositories;

    /**
     * Сценарий настройки привязки.
     *
     * @param localRepositories можно ли привязать репозиторий в файловой системе сервера ({@code file://});
     *                          в работе — нет: администратор получил бы доступ к любому каталогу сервера
     */
    public GitBindingConfigureCommandHandler(InterchangeKernel kernel, GitBindingRepository bindings,
                                             boolean localRepositories) {
        this.kernel = kernel;
        this.bindings = bindings;
        this.localRepositories = localRepositories;
    }

    /**
     * Новая привязка заменяет прежнюю. Тот же адрес и ветка — последний коммит сервера
     * сохраняется: по нему видно, не переписана ли история.
     */
    public GitBindingResponse handle(EditorIdentity actor, ConfigureGitBindingCommand command) {
        return kernel.run(InterchangeOperation.CONFIGURE_GIT_BINDING, actor, () -> {
            WorkspaceId workspace = new WorkspaceId(command.workspaceId());
            GitBinding configured = configured(workspace, command);
            if (configured.local() && !localRepositories) {
                throw new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE,
                        Message.of(InterchangeMessages.GIT_URL_LOCAL, configured.repoUrl()), Map.of());
            }
            return kernel.unitOfWork().write(() -> {
                if (!bindings.workspaceExists(workspace)) {
                    throw new InterchangeException(InterchangeCodes.NOT_FOUND, Failure.NOT_FOUND,
                            Message.of(InterchangeMessages.WORKSPACE_NOT_FOUND, workspace), Map.of());
                }
                GitBinding binding = bindings.find(workspace).filter(old -> old.sameRemote(configured))
                        .map(GitBinding::lastCommitSha)
                        .map(configured::withHead)
                        .orElse(configured);
                bindings.save(binding);
                return GitDataMapper.binding(binding);
            });
        });
    }

    private static GitBinding configured(WorkspaceId workspace, ConfigureGitBindingCommand command) {
        if (command.tokenRef() == null) {
            throw new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE,
                    Message.of(InterchangeMessages.GIT_PULL_FIELD_MISSING, "tokenRef"), Map.of());
        }
        try {
            return GitBinding.configure(workspace, command.repoUrl(), command.branch(),
                    SecretRef.of(command.tokenRef()), command.authors().stream()
                            .map(a -> new GitAuthor(a.subject(), a.name(), a.email())).toList());
        } catch (InvalidValueException e) {
            throw new InterchangeException(InterchangeCodes.INVALID_REQUEST, Failure.UNPROCESSABLE, e.reason(),
                    Map.of());
        }
    }
}
