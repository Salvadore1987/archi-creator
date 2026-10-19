package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

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
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorAdminService;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorMessages;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Состояние помощника, выключатель и лимит, расход за месяц. */
@RestController
@RequestMapping("/api/v1/ai")
public class AdvisorSettingsController {

    private final AdvisorAdminService admin;

    public AdvisorSettingsController(AdvisorAdminService admin) {
        this.admin = admin;
    }

    /** Включён ли помощник и в каком состоянии бюджет текущего месяца. */
    @GetMapping("/status")
    public StatusDto status(Requester requester, @RequestParam(required = false) UUID workspaceId) {
        return DtoMapper.status(admin.status(requester, workspace(workspaceId)));
    }

    /** Выключатель и месячный лимит токенов пространства. */
    @GetMapping("/settings")
    public SettingsDto settings(Requester requester, @RequestParam(required = false) UUID workspaceId) {
        return DtoMapper.settings(admin.settings(requester, workspace(workspaceId)));
    }

    /** Изменить выключатель и лимит; без поля {@code enabled} запрос не разбирается. */
    @PutMapping("/settings")
    public SettingsDto change(Requester requester, @RequestParam(required = false) UUID workspaceId,
                              @RequestBody SettingsDto body) {
        if (body.enabled() == null) {
            throw AdvisorDomainException.invalid(Message.of(AdvisorMessages.FIELD_REQUIRED, "enabled"));
        }
        return DtoMapper.settings(admin.change(requester, workspace(workspaceId), body.enabled(),
                body.monthlyTokenLimit()));
    }

    /** Расход токенов за период — по авторам и по функциям. */
    @GetMapping("/usage")
    public UsageDto usage(Requester requester, @RequestParam(required = false) UUID workspaceId,
                          @RequestParam(required = false) String period) {
        return DtoMapper.usage(admin.usage(requester, workspace(workspaceId), period));
    }

    private static WorkspaceId workspace(UUID workspaceId) {
        return workspaceId == null ? null : WorkspaceId.of(workspaceId);
    }
}
