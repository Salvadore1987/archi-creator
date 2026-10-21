package uz.salvadore.hamkorbank.archi.interchange.application.service;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ConfigureGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.DisableGitBindingCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.GetGitBindingQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.GitBindingResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitBindingConfigureCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitBindingDisableCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.GitBindingGetQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.GitBindingApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Привязка к Git: делегирует в handler'ы сценариев. */
public final class GitBindingApplicationServiceImpl implements GitBindingApplicationService {

    private final GitBindingGetQueryHandler get;
    private final GitBindingConfigureCommandHandler configure;
    private final GitBindingDisableCommandHandler disable;

    public GitBindingApplicationServiceImpl(GitBindingGetQueryHandler get, GitBindingConfigureCommandHandler configure,
                                            GitBindingDisableCommandHandler disable) {
        this.get = get;
        this.configure = configure;
        this.disable = disable;
    }

    @Override
    public GitBindingResponse getGitBinding(EditorIdentity actor, GetGitBindingQuery query) {
        return get.handle(actor, query);
    }

    @Override
    public GitBindingResponse configureGitBinding(EditorIdentity actor, ConfigureGitBindingCommand command) {
        return configure.handle(actor, command);
    }

    @Override
    public void disableGitBinding(EditorIdentity actor, DisableGitBindingCommand command) {
        disable.handle(actor, command);
    }
}
