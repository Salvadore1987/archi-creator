package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelContentQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Текущее содержимое модели в формате {@code .archimate} и номер последней версии — основа
 * для замены и сравнения. Модель видна актору на запись и не удалена — читать её для замены
 * иначе незачем. Роль проверяет вызывающий.
 */
public final class ModelContentGetQueryHandler {

    private final ModelingKernel kernel;
    private final VersionContents contents;

    public ModelContentGetQueryHandler(ModelingKernel kernel, VersionContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Заголовок, последняя версия и содержимое. */
    public ModelContentResponse handle(EditorIdentity actor, GetModelContentQuery query) {
        ModelId modelId = ModelId.of(query.modelId());
        return kernel.unitOfWork().read(() -> {
            ModelHeader header = kernel.visibleHeader(modelId, actor, AclAccess.WRITE);
            header.requireExportable();
            return new ModelContentResponse(ModelDataMapper.summary(header),
                    kernel.versions().last(modelId).map(ModelVersion::versionNo).orElse(null),
                    contents.currentXml(modelId));
        });
    }
}
