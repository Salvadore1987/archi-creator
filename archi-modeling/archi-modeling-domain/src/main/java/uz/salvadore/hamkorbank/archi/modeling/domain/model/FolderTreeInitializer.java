package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;

/**
 * Девять корневых папок новой модели — сразу при создании, а не по первому элементу:
 * файл Archi без них невалиден (UC-MDL-001, INV-MDL-009).
 */
public final class FolderTreeInitializer {

    private FolderTreeInitializer() {
    }

    public static List<ModelFolder> rootFolders(Supplier<FolderId> ids, Supplier<ArchiId> archiIds) {
        List<ModelFolder> roots = new ArrayList<>();
        for (FolderType type : FolderType.values()) {
            roots.add(root(type, ids.get(), archiIds.get(), SortOrder.ofPosition(type.ordinal())));
        }
        return roots;
    }

    public static ModelFolder root(FolderType type, FolderId id, ArchiId archiId, SortOrder order) {
        return new ModelFolder(id, Optional.empty(), archiId, type.defaultName(), Optional.of(type), order,
                Optional.empty());
    }
}
