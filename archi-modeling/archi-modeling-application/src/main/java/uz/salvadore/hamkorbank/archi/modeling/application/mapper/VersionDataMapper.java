package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/** Версия модели — в результат сценария, без снимка. */
public final class VersionDataMapper {

    private VersionDataMapper() {
    }

    /** Версия; есть ли снимок в базе — флагом. */
    public static VersionResponse version(ModelVersion v) {
        return new VersionResponse(v.versionNo(), v.author(), v.comment(), v.label(), v.createdAt(),
                v.hasSnapshot(), v.contentHash(), v.gitSha());
    }
}
