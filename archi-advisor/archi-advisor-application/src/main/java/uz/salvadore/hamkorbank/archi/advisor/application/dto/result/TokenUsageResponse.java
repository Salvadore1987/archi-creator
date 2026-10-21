package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

import java.util.List;

/**
 * Расход месяца с разбиением по авторам и функциям.
 *
 * @param period месяц {@code ГГГГ-ММ}
 */
public record TokenUsageResponse(String period, long limitTokens, long consumedTokens, String budgetStatus,
                                 List<TokenUsageRowResponse> byUser, List<TokenUsageRowResponse> byFunction) {
}
