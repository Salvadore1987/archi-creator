package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.Objects;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;

/**
 * Папка дерева модели. Корневая — с {@code folderType} и без родителя,
 * пользовательская — с родителем и без типа.
 */
public record ModelFolder(FolderId id, Optional<FolderId> parentId, ArchiId archiId, String name,
                          Optional<FolderType> folderType, SortOrder sortOrder, Optional<RawXml> rawXml) {

    public ModelFolder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(folderType, "folderType");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(rawXml, "rawXml");
        Names.limited(name, Names.FOLDER);
        if (folderType.isPresent() == parentId.isPresent()) {
            throw new InvalidValueException(ModelingMessages.FOLDER_TYPE_OR_PARENT);
        }
    }

    public boolean root() {
        return folderType.isPresent();
    }

    public ModelFolder withName(String newName) {
        return new ModelFolder(id, parentId, archiId, newName, folderType, sortOrder, rawXml);
    }

    public ModelFolder movedTo(FolderId newParent, SortOrder newOrder) {
        return new ModelFolder(id, Optional.of(newParent), archiId, name, folderType, newOrder, rawXml);
    }
}
