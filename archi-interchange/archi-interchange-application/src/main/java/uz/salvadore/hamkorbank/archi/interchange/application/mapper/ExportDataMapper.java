package uz.salvadore.hamkorbank.archi.interchange.application.mapper;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ExportFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.Artifact;

/** Файл выгрузки — в результат сценария. */
public final class ExportDataMapper {

    private ExportDataMapper() {
    }

    /** Артефакт с версией и числом записей отчёта о потерях. */
    public static ExportFileResponse file(Artifact artifact, long versionNo, int lossCount) {
        return new ExportFileResponse(artifact.bytes(), artifact.mediaType(), artifact.fileName(),
                artifact.hash().value(), versionNo, lossCount);
    }
}
