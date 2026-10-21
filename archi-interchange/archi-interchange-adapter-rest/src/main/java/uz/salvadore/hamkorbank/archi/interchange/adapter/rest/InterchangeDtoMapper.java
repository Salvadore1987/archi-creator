package uz.salvadore.hamkorbank.archi.interchange.adapter.rest;

import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ImportResult;
import uz.salvadore.hamkorbank.archi.interchange.adapter.rest.dto.Dtos.ImportSettings;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportPolicyResponse;

/** Импорт файла и его настройка: результат сценария — в DTO. */
final class InterchangeDtoMapper {

    private InterchangeDtoMapper() {
    }

    /** Итог импорта — одинаковый у принятого и отклонённого: отказ несёт его в подробностях. */
    static ImportResult importResult(ImportArchimateFileResponse result) {
        return new ImportResult(result.sessionId(), result.status(), result.modelId(), result.versionNo(),
                result.replayed(), FindingMapper.findings(result.findings()));
    }

    /** Настройка импорта пространства. */
    static ImportSettings settings(ImportPolicyResponse policy) {
        return new ImportSettings(policy.strictImport());
    }
}
