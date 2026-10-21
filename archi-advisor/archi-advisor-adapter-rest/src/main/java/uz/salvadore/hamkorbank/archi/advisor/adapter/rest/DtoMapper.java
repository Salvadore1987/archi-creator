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
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorAnswerResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorSettingsResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorStatusResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.ElementRefResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.FindingResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.ModelPlanResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.SessionMetaResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.TokenUsageResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.TokenUsageRowResponse;

/** Перевод результатов сценариев помощника в DTO REST. */
final class DtoMapper {

    private DtoMapper() {
    }

    static ElementRefDto ref(ElementRefResponse ref) {
        return new ElementRefDto(ref.archiId(), ref.name());
    }

    static FindingDto finding(FindingResponse f) {
        return new FindingDto(f.id(), f.severity(), f.code(), f.message(), f.targetKind(), ref(f.target()),
                f.suggestion());
    }

    static ModelPlanDto plan(ModelPlanResponse plan) {
        return new ModelPlanDto(
                plan.elements().stream().map(e -> new PlannedElementDto(e.archiType(), e.name(),
                        e.existingRef() == null ? null : ref(e.existingRef()))).toList(),
                plan.relationships().stream().map(r -> new PlannedRelationDto(r.archiType(), r.sourceName(),
                        r.targetName(), r.permitted())).toList(),
                plan.notes());
    }

    static AnswerDto answer(AdvisorAnswerResponse answer) {
        return new AnswerDto(answer.markdown(), answer.references().stream().map(DtoMapper::ref).toList());
    }

    static SessionMetaDto meta(SessionMetaResponse meta) {
        return new SessionMetaDto(meta.sessionId(), meta.outcome(), meta.rejectionReason(), meta.promptTokens(),
                meta.completionTokens(), meta.contextTruncated());
    }

    static StatusDto status(AdvisorStatusResponse status) {
        return new StatusDto(status.workspaceId(), status.enabled(), status.budgetStatus(), status.period(),
                status.resetsAt());
    }

    static SettingsDto settings(AdvisorSettingsResponse settings) {
        return new SettingsDto(settings.enabled(), settings.monthlyTokenLimit());
    }

    static UsageDto usage(TokenUsageResponse usage) {
        return new UsageDto(usage.period(), usage.limitTokens(), usage.consumedTokens(), usage.budgetStatus(),
                usage.byUser().stream().map(DtoMapper::row).toList(),
                usage.byFunction().stream().map(DtoMapper::row).toList());
    }

    private static UsageRowDto row(TokenUsageRowResponse row) {
        return new UsageRowDto(row.key(), row.requests(), row.rejected(), row.promptTokens(), row.completionTokens());
    }
}
