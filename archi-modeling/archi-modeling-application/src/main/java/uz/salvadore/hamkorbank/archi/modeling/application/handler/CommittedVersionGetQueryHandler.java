package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetCommittedVersionQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.CommittedVersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.VersionDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Зафиксированная версия для публикации в Git — системное чтение, без актора: публикует
 * фоновый обработчик. Модели или версии нет, снимок очищен — пусто.
 */
public final class CommittedVersionGetQueryHandler {

    private final ModelingKernel kernel;

    public CommittedVersionGetQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Версия со снимком или пусто. */
    public Optional<CommittedVersionResponse> handle(GetCommittedVersionQuery query) {
        ModelId modelId = ModelId.of(query.modelId());
        return kernel.unitOfWork().read(() -> kernel.models().findHeader(modelId).flatMap(header ->
                kernel.versions().find(modelId, query.versionNo()).filter(ModelVersion::hasSnapshot)
                        .map(v -> new CommittedVersionResponse(ModelDataMapper.summary(header),
                                VersionDataMapper.version(v), Snapshots.gunzip(v.requireSnapshot())))));
    }
}
