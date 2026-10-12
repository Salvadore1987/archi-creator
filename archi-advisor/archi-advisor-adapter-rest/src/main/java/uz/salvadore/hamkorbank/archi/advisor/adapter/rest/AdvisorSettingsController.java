package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

import java.util.Optional;
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
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorException;
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

    @GetMapping("/status")
    public StatusDto status(Requester requester, @RequestParam Optional<UUID> workspaceId) {
        return DtoMapper.status(admin.status(requester, workspace(workspaceId)));
    }

    @GetMapping("/settings")
    public SettingsDto settings(Requester requester, @RequestParam Optional<UUID> workspaceId) {
        return DtoMapper.settings(admin.settings(requester, workspace(workspaceId)));
    }

    @PutMapping("/settings")
    public SettingsDto change(Requester requester, @RequestParam Optional<UUID> workspaceId,
                              @RequestBody SettingsDto body) {
        if (body.enabled() == null) {
            throw AdvisorException.invalid(Message.of(AdvisorMessages.FIELD_REQUIRED, "enabled"));
        }
        return DtoMapper.settings(admin.change(requester, workspace(workspaceId), body.enabled(),
                Optional.ofNullable(body.monthlyTokenLimit())));
    }

    @GetMapping("/usage")
    public UsageDto usage(Requester requester, @RequestParam Optional<UUID> workspaceId,
                          @RequestParam Optional<String> period) {
        return DtoMapper.usage(admin.usage(requester, workspace(workspaceId), period));
    }

    private static Optional<WorkspaceId> workspace(Optional<UUID> workspaceId) {
        return workspaceId.map(WorkspaceId::of);
    }
}
