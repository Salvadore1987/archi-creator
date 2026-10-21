package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

import java.util.UUID;

/** Замечание ревью со ссылкой на объект модели и предложением исправления. */
public record FindingResponse(UUID id, String severity, String code, String message, String targetKind,
                              ElementRefResponse target, String suggestion) {
}
