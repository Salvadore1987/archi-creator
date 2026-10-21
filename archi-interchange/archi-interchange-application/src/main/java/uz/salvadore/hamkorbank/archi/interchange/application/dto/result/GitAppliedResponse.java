package uz.salvadore.hamkorbank.archi.interchange.application.dto.result;

import java.util.List;
import java.util.UUID;

/**
 * Итог применения импорта по ссылке или слияния: сессия, версия и признак, создана ли она.
 *
 * @param status    состояние сессии импорта — {@code APPLIED}
 * @param created   ложь — содержимое совпало с текущим, версия прежняя
 * @param commitSha применённый коммит
 */
public record GitAppliedResponse(UUID sessionId, String status, UUID modelId, long versionNo, boolean created,
                                 String commitSha, List<ImportFindingResponse> findings) {

    public GitAppliedResponse {
        findings = List.copyOf(findings);
    }
}
