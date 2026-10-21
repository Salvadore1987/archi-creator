package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListVersionsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.VersionResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.VersionDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/**
 * История версий модели, от новых к старым, без снимков, — страницами: каждое сохранение
 * добавляет версию, и история растёт без предела.
 */
public final class VersionListQueryHandler {

    /** Версий на странице, если клиент не задал. */
    static final int DEFAULT_PAGE = 50;
    /** Больше версий на странице не отдаётся, сколько бы ни просили. */
    static final int MAX_PAGE = 200;

    private final ModelingKernel kernel;

    public VersionListQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Версии модели, видимой на чтение. */
    public List<VersionResponse> handle(EditorIdentity actor, ListVersionsQuery query) {
        return kernel.run(Operation.LIST_VERSIONS, actor, () -> kernel.unitOfWork().read(() -> {
            ModelId modelId = ModelId.of(query.modelId());
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            return kernel.versions().page(modelId, before(query.before()), pageSize(query.size())).stream()
                    .map(VersionDataMapper::version).toList();
        }));
    }

    /** Размер страницы: не задан — по умолчанию, больше предела — предел, меньше единицы — отказ. */
    static int pageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_PAGE;
        }
        if (requested < 1) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.PAGE_SIZE_INVALID, MAX_PAGE));
        }
        return Math.min(requested, MAX_PAGE);
    }

    /** Граница страницы: не задана — с последней версии. */
    private static long before(Long before) {
        if (before == null) {
            return Long.MAX_VALUE;
        }
        if (before < 1) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.VERSION_NUMBER_POSITIVE));
        }
        return before;
    }
}
