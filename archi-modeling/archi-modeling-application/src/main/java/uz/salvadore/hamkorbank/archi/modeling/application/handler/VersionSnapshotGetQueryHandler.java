package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetVersionSnapshotQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionSnapshotResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Снимок версии для выгрузки: указанной или последней. Удалённая модель не выгружается
 * ({@code 409}); очищенный снимок возвращается из Git, а без Git — {@code 410}. Роль проверяет
 * вызывающий: у каждой выгрузки своя строка в таблице доступа.
 */
public final class VersionSnapshotGetQueryHandler {

    private final ModelingKernel kernel;
    private final VersionContents contents;

    public VersionSnapshotGetQueryHandler(ModelingKernel kernel, VersionContents contents) {
        this.kernel = kernel;
        this.contents = contents;
    }

    /** Модель, номер версии и её {@code .archimate}. */
    public VersionSnapshotResponse handle(EditorIdentity actor, GetVersionSnapshotQuery query) {
        ModelId modelId = ModelId.of(query.modelId());
        Located located = kernel.unitOfWork().read(() -> {
            ModelHeader header = kernel.visibleHeader(modelId, actor, AclAccess.READ);
            header.requireExportable();
            ModelVersion version = query.versionNo() != null ? contents.requireVersion(modelId, query.versionNo())
                    : kernel.versions().last(modelId).orElseThrow(() -> ModelingDomainException.notFound(
                            Message.of(ModelingMessages.MODEL_VERSION, modelId)));
            return new Located(header, version);
        });
        return new VersionSnapshotResponse(ModelDataMapper.summary(located.header()), located.version().versionNo(),
                contents.content(located.header(), located.version()));
    }

    /** Версия и заголовок её модели, прочитанные в транзакции, — до обращения к архиву вне её. */
    private record Located(ModelHeader header, ModelVersion version) {
    }
}
