package uz.salvadore.hamkorbank.archi.advisor.adapter.rest;

import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.AnswerDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.ElementRefDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.FindingDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.ModelPlanDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.PlannedElementDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.PlannedRelationDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.SessionMetaDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.SettingsDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.StatusDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.UsageDto;
import uz.salvadore.hamkorbank.archi.advisor.adapter.rest.dto.Dtos.UsageRowDto;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AuditLog;
import uz.salvadore.hamkorbank.archi.advisor.application.service.AdvisorAdminService;
import uz.salvadore.hamkorbank.archi.advisor.application.service.SessionMeta;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorAnswer;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ElementRef;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.Finding;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ModelPlan;

/** Перевод результатов помощника в DTO. */
final class DtoMapper {

    private DtoMapper() {
    }

    static ElementRefDto ref(ElementRef ref) {
        return new ElementRefDto(ref.archiId(), ref.name());
    }

    static FindingDto finding(Finding f) {
        return new FindingDto(f.id().value(), f.severity().name(), f.code(), f.message(), f.targetKind().name(),
                ref(f.target()), f.suggestion().orElse(null));
    }

    static ModelPlanDto plan(ModelPlan plan) {
        return new ModelPlanDto(
                plan.elements().stream().map(e -> new PlannedElementDto(e.archiType(), e.name(),
                        e.existingRef().map(DtoMapper::ref).orElse(null))).toList(),
                plan.relationships().stream().map(r -> new PlannedRelationDto(r.archiType(), r.sourceName(),
                        r.targetName(), r.permitted())).toList(),
                plan.notes().orElse(null));
    }

    static AnswerDto answer(AdvisorAnswer answer) {
        return new AnswerDto(answer.markdown(), answer.references().stream().map(DtoMapper::ref).toList());
    }

    static SessionMetaDto meta(SessionMeta meta) {
        return new SessionMetaDto(meta.sessionId().value(), meta.outcome().name(),
                meta.rejectionReason().map(Enum::name).orElse(null), meta.promptTokens(), meta.completionTokens(),
                meta.contextTruncated());
    }

    static StatusDto status(AdvisorAdminService.Status status) {
        return new StatusDto(status.workspaceId().value(), status.enabled(), status.budgetStatus().name(),
                status.period().value(), status.resetsAt());
    }

    static SettingsDto settings(AdvisorAdminService.Settings settings) {
        return new SettingsDto(settings.enabled(), settings.monthlyTokenLimit());
    }

    static UsageDto usage(AdvisorAdminService.Usage usage) {
        return new UsageDto(usage.period().value(), usage.limitTokens(), usage.consumedTokens(),
                usage.budgetStatus().name(), usage.byUser().stream().map(DtoMapper::row).toList(),
                usage.byFunction().stream().map(DtoMapper::row).toList());
    }

    private static UsageRowDto row(AuditLog.UsageRow row) {
        return new UsageRowDto(row.key(), row.requests(), row.rejected(), row.promptTokens(), row.completionTokens());
    }
}
