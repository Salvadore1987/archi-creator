package uz.salvadore.hamkorbank.archi.advisor.application.dto.result;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Замечание ревью со ссылкой на объект модели и предложением исправления. */
public record FindingResponse(UUID id, String severity, String code, String message, String targetKind,
                              ElementRefResponse target, @Nullable String suggestion) {
}
