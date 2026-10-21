package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.advisor.application.access.AdvisorOperation;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.query.GetTokenUsageQuery;
import uz.salvadore.hamkorbank.archi.advisor.application.dto.result.TokenUsageResponse;
import uz.salvadore.hamkorbank.archi.advisor.application.mapper.AdvisorDataMapper;
import uz.salvadore.hamkorbank.archi.advisor.application.port.AuditLog;
import uz.salvadore.hamkorbank.archi.advisor.application.port.WorkspaceDirectory;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.BudgetPeriod;
import uz.salvadore.hamkorbank.archi.advisor.domain.budget.TokenBudget;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.AdvisorDomainException;
import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/** Расход месяца: лимит, израсходовано, разбиение по пользователям и функциям — из аудита. */
public final class TokenUsageGetQueryHandler {

    private final AdvisorKernel kernel;
    private final AdvisorBudgets budgets;
    private final WorkspaceDirectory workspaces;
    private final AuditLog audit;

    public TokenUsageGetQueryHandler(AdvisorKernel kernel, AdvisorBudgets budgets, WorkspaceDirectory workspaces,
                                     AuditLog audit) {
        this.kernel = kernel;
        this.budgets = budgets;
        this.workspaces = workspaces;
        this.audit = audit;
    }

    /** Расход месяца запроса; месяц не задан — текущий. */
    public TokenUsageResponse handle(Requester actor, GetTokenUsageQuery query) {
        return kernel.run(AdvisorOperation.VIEW_TOKEN_USAGE, actor, () -> {
            BudgetPeriod month = Optional.ofNullable(query.period()).map(TokenUsageGetQueryHandler::month)
                    .orElseGet(() -> budgets.period(kernel.now()));
            WorkspaceId workspaceId = workspaces.resolve(AdvisorDataMapper.workspace(query.workspaceId()));
            return kernel.unitOfWork().read(() -> {
                TokenBudget budget = budgets.current(workspaceId, month);
                return new TokenUsageResponse(month.value(), budget.limitTokens(), budget.consumedTokens(),
                        budget.status().name(), AdvisorDataMapper.usageRows(audit.byUser(workspaceId, month)),
                        AdvisorDataMapper.usageRows(audit.byFunction(workspaceId, month)));
            });
        });
    }

    private static BudgetPeriod month(String period) {
        try {
            return BudgetPeriod.parse(period);
        } catch (IllegalArgumentException invalid) {
            throw AdvisorDomainException.invalid(invalid);
        }
    }
}
