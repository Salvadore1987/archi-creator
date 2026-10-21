package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Состояние помощника в пространстве — по нему интерфейс показывает панель.
 *
 * @param period   месяц бюджета {@code ГГГГ-ММ}
 * @param resetsAt первый день следующего месяца
 */
public record AdvisorStatusResponse(UUID workspaceId, boolean enabled, String budgetStatus, String period,
                                    LocalDate resetsAt) {
}
