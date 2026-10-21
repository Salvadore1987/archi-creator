package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.SettingsDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.StatusDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.UsageDto;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ConfigureAdvisorCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorSettingsQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetAdvisorStatusQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetTokenUsageQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.port.in.AdvisorAdminApplicationService;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;

/** Состояние помощника, выключатель и лимит, расход за месяц. */
@RestController
@RequestMapping("/api/v1/ai")
public class AdvisorSettingsController {

    private final AdvisorAdminApplicationService admin;

    public AdvisorSettingsController(AdvisorAdminApplicationService admin) {
        this.admin = admin;
    }

    /** Включён ли помощник и в каком состоянии бюджет текущего месяца. */
    @GetMapping("/status")
    public StatusDto status(Requester requester, @RequestParam(required = false) UUID workspaceId) {
        return DtoMapper.status(admin.getAdvisorStatus(requester, new GetAdvisorStatusQuery(workspaceId)));
    }

    /** Выключатель и месячный лимит токенов пространства. */
    @GetMapping("/settings")
    public SettingsDto settings(Requester requester, @RequestParam(required = false) UUID workspaceId) {
        return DtoMapper.settings(admin.getAdvisorSettings(requester, new GetAdvisorSettingsQuery(workspaceId)));
    }

    /** Изменить выключатель и лимит; без поля {@code enabled} — отказ формы запроса. */
    @PutMapping("/settings")
    public SettingsDto change(Requester requester, @RequestParam(required = false) UUID workspaceId,
                              @Valid @RequestBody SettingsDto body) {
        return DtoMapper.settings(admin.configureAdvisor(requester,
                new ConfigureAdvisorCommand(workspaceId, body.enabled(), body.monthlyTokenLimit())));
    }

    /** Расход токенов за период — по авторам и по функциям. */
    @GetMapping("/usage")
    public UsageDto usage(Requester requester, @RequestParam(required = false) UUID workspaceId,
                          @RequestParam(required = false) String period) {
        return DtoMapper.usage(admin.getTokenUsage(requester, new GetTokenUsageQuery(workspaceId, period)));
    }
}
