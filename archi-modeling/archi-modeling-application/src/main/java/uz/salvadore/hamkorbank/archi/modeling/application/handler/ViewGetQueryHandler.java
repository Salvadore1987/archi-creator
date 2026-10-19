package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetViewQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ViewDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;

/** Представление по требованию: узлы, рёбра и их оформление; модель должна быть видна на чтение. */
public final class ViewGetQueryHandler {

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewGetQueryHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Представление целиком. */
    public ViewResponse handle(EditorIdentity actor, GetViewQuery query) {
        return kernel.run(Operation.OPEN_VIEW, actor, () -> kernel.unitOfWork().read(() -> {
            View view = lookup.view(query.viewId());
            kernel.visibleHeader(view.modelId(), actor, AclAccess.READ);
            return ViewDataMapper.view(view);
        }));
    }
}
