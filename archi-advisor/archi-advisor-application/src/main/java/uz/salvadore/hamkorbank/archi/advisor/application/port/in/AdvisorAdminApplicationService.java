package uz.salvadore.hamkorbank.archi.advisor.application.port.in;

import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ConfigureAdvisorCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorSettingsQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorStatusQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetTokenUsageQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorSettingsResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorStatusResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.TokenUsageResponse;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;

/** Управление помощником: состояние для интерфейса, выключатель и лимит, расход. */
public interface AdvisorAdminApplicationService {

    /** Включён ли помощник и в каком состоянии бюджет месяца. */
    AdvisorStatusResponse getAdvisorStatus(Requester actor, GetAdvisorStatusQuery query);

    /** Выключатель и действующий лимит. */
    AdvisorSettingsResponse getAdvisorSettings(Requester actor, GetAdvisorSettingsQuery query);

    /** Новый выключатель и лимит; расход месяца не меняется. */
    AdvisorSettingsResponse configureAdvisor(Requester actor, ConfigureAdvisorCommand command);

    /** Расход месяца: лимит, израсходовано, разбиение по авторам и функциям. */
    TokenUsageResponse getTokenUsage(Requester actor, GetTokenUsageQuery query);
}
