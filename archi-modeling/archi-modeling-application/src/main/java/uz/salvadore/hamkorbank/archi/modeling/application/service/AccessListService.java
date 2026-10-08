package uz.salvadore.hamkorbank.archi.modeling.application.service;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclAccess;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.AclEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.access.ModelAccessList;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;

/** UC-MDL-007: список доступа к модели (FR-29, INV-MDL-011). */
public final class AccessListService {

    private final ModelingKernel kernel;

    public AccessListService(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    public ModelAccessList get(EditorIdentity actor, ModelId modelId) {
        return kernel.run(Operation.OPEN_MODEL, actor, () -> kernel.unitOfWork.read(() -> {
            kernel.visibleHeader(modelId, actor, AclAccess.READ);
            return kernel.accessLists.find(modelId);
        }));
    }

    /** Список заменяется целиком: команда описывает итог, а не правку. */
    public ModelAccessList replace(EditorIdentity actor, ModelId modelId, List<AclEntry> entries) {
        return kernel.run(Operation.MANAGE_ACCESS, actor, () -> kernel.unitOfWork.write(() -> {
            ModelHeader header = kernel.visibleHeader(modelId, actor, AclAccess.READ);
            ModelAccessList.requireManager(actor, header.createdBy());
            ModelAccessList list = new ModelAccessList(modelId, entries);
            kernel.accessLists.save(list);
            return list;
        }));
    }
}
