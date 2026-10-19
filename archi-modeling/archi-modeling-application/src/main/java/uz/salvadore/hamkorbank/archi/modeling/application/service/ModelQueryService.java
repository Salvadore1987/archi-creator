package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewId;

/** Представление по требованию — до перевода области представлений на входной порт. */
public final class ModelQueryService {

    private final ModelingKernel kernel;

    public ModelQueryService(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Payload представления по требованию (OpenView). */
    public View openView(EditorIdentity actor, ViewId viewId) {
        return kernel.run(Operation.OPEN_VIEW, actor, () -> kernel.unitOfWork.read(() -> {
            View view = kernel.views.load(viewId)
                    .orElseThrow(() -> ModelingDomainException.notFound(Message.of(ModelingMessages.VIEW, viewId)));
            kernel.visibleHeader(view.modelId(), actor, AclAccess.READ);
            return view;
        }));
    }
}
