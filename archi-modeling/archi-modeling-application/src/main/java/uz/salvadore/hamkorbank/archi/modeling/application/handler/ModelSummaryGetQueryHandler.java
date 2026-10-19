package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelSummaryQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * Заголовок модели, видимой актору на чтение; иначе «не найдено», как везде. Роль проверяет
 * вызывающий — так другой контекст узнаёт рабочее пространство модели.
 */
public final class ModelSummaryGetQueryHandler {

    private final ModelingKernel kernel;

    public ModelSummaryGetQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Заголовок модели. */
    public ModelSummaryResponse handle(EditorIdentity actor, GetModelSummaryQuery query) {
        return kernel.unitOfWork().read(() -> ModelDataMapper.summary(
                kernel.visibleHeader(ModelId.of(query.modelId()), actor, AclAccess.READ)));
    }
}
