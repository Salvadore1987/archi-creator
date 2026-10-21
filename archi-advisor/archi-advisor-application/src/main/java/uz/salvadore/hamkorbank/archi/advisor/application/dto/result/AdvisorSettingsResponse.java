package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

/** Выключатель и действующий месячный лимит. */
public record AdvisorSettingsResponse(boolean enabled, long monthlyTokenLimit) {
}
