package uz.salvadore.hamkorbank.archi.advisor.application.mapper;

import java.util.List;
import java.util.UUID;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ExplainElementCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.GenerateDocumentationCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.PlanModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.command.ReviewModelCommand;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.AdvisorAnswerResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.ElementRefResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.FindingResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.ModelPlanResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.PlannedElementResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.PlannedRelationResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.SessionMetaResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.TokenUsageRowResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AuditLog;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ViewId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorAnswer;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorRequest;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.AdvisorSession;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ElementRef;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.Finding;
import uz.salvadore.hamkorbank.archi.advisor.domain.session.ModelPlan;

/** Команды помощника — в запрос домена, результаты сессии — в DTO из простых значений. */
public final class AdvisorDataMapper {

    private AdvisorDataMapper() {
    }

    /** Запрос ревью. */
    public static AdvisorRequest request(ReviewModelCommand command) {
        return AdvisorRequest.review(ModelId.of(command.modelId()), view(command.viewId()));
    }

    /** Запрос подсказки. */
    public static AdvisorRequest request(ExplainElementCommand command) {
        return AdvisorRequest.explain(ModelId.of(command.modelId()), command.elementId());
    }

    /** Запрос плана. */
    public static AdvisorRequest request(PlanModelCommand command) {
        return AdvisorRequest.plan(ModelId.of(command.modelId()), command.prompt());
    }

    /** Запрос документации. */
    public static AdvisorRequest request(GenerateDocumentationCommand command) {
        return AdvisorRequest.document(ModelId.of(command.modelId()), view(command.viewId()), command.elementId());
    }

    /** Пространство запроса; {@code null} — по умолчанию. */
    public static WorkspaceId workspace(UUID workspaceId) {
        return workspaceId == null ? null : WorkspaceId.of(workspaceId);
    }

    /** Замечание ревью. */
    public static FindingResponse finding(Finding finding) {
        return new FindingResponse(finding.id().value(), finding.severity().name(), finding.code(), finding.message(),
                finding.targetKind().name(), ref(finding.target()), finding.suggestion());
    }

    /** План. */
    public static ModelPlanResponse plan(ModelPlan plan) {
        return new ModelPlanResponse(
                plan.elements().stream().map(e -> new PlannedElementResponse(e.archiType(), e.name(),
                        e.existingRef() == null ? null : ref(e.existingRef()))).toList(),
                plan.relationships().stream().map(r -> new PlannedRelationResponse(r.archiType(), r.sourceName(),
                        r.targetName(), r.permitted())).toList(),
                plan.notes());
    }

    /** Ответ подсказки или документации. */
    public static AdvisorAnswerResponse answer(AdvisorAnswer answer) {
        return new AdvisorAnswerResponse(answer.markdown(),
                answer.references().stream().map(AdvisorDataMapper::ref).toList());
    }

    /** Итог сессии для последнего кадра потока. */
    public static SessionMetaResponse meta(AdvisorSession session) {
        return new SessionMetaResponse(session.id().value(), session.status().name(),
                session.rejectionReason() == null ? null : session.rejectionReason().name(),
                session.usage().promptTokens(), session.usage().completionTokens(), session.contextTruncated());
    }

    /** Строки разбивки расхода. */
    public static List<TokenUsageRowResponse> usageRows(List<AuditLog.UsageRow> rows) {
        return rows.stream().map(row -> new TokenUsageRowResponse(row.key(), row.requests(), row.rejected(),
                row.promptTokens(), row.completionTokens())).toList();
    }

    private static ElementRefResponse ref(ElementRef ref) {
        return new ElementRefResponse(ref.archiId(), ref.name());
    }

    private static ViewId view(UUID viewId) {
        return viewId == null ? null : ViewId.of(viewId);
    }
}
