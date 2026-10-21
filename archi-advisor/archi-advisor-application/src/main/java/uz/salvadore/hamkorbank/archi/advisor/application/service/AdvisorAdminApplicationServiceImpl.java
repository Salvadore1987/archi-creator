package uz.salvadore.hamkorbank.archi.advisor.application.service;

import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ConfigureAdvisorCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorSettingsQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorStatusQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetTokenUsageQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorSettingsResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorStatusResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.TokenUsageResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorSettingsConfigureCommandHandler;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorSettingsGetQueryHandler;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.AdvisorStatusGetQueryHandler;
import uz.salvadore.hamkorbank.archi.advisor.application.handler.TokenUsageGetQueryHandler;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorAdminApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;

/** Входной порт управления помощником: делегирует handler'ам сценариев. */
public final class AdvisorAdminApplicationServiceImpl implements AdvisorAdminApplicationService {

    private final AdvisorStatusGetQueryHandler status;
    private final AdvisorSettingsGetQueryHandler settings;
    private final AdvisorSettingsConfigureCommandHandler configure;
    private final TokenUsageGetQueryHandler usage;

    public AdvisorAdminApplicationServiceImpl(AdvisorStatusGetQueryHandler status,
                                              AdvisorSettingsGetQueryHandler settings,
                                              AdvisorSettingsConfigureCommandHandler configure,
                                              TokenUsageGetQueryHandler usage) {
        this.status = status;
        this.settings = settings;
        this.configure = configure;
        this.usage = usage;
    }

    @Override
    public AdvisorStatusResponse getAdvisorStatus(Requester actor, GetAdvisorStatusQuery query) {
        return status.handle(actor, query);
    }

    @Override
    public AdvisorSettingsResponse getAdvisorSettings(Requester actor, GetAdvisorSettingsQuery query) {
        return settings.handle(actor, query);
    }

    @Override
    public AdvisorSettingsResponse configureAdvisor(Requester actor, ConfigureAdvisorCommand command) {
        return configure.handle(actor, command);
    }

    @Override
    public TokenUsageResponse getTokenUsage(Requester actor, GetTokenUsageQuery query) {
        return usage.handle(actor, query);
    }
}
