package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.Objects;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ArchiId;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.InvalidValueException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.RawXml;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;

/**
 * Папка дерева модели. Корневая — с {@code folderType} и без родителя,
 * пользовательская — с родителем и без типа.
 *
 * @param parentId   родитель; {@code null} — у корневой папки
 * @param folderType тип корневой папки; {@code null} — у пользовательской
 * @param rawXml     остаток XML; {@code null} — нет
 */
public record ModelFolder(FolderId id, FolderId parentId, ArchiId archiId, String name,
                          FolderType folderType, SortOrder sortOrder, RawXml rawXml) {

    public ModelFolder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archiId, "archiId");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Names.limited(name, Names.FOLDER);
        if ((folderType != null) == (parentId != null)) {
            throw new InvalidValueException(ModelingMessages.FOLDER_TYPE_OR_PARENT);
        }
    }

    /** Корневая ли папка: у корня есть тип и нет родителя. */
    public boolean root() {
        return folderType != null;
    }

    /** Та же папка с новым именем. */
    public ModelFolder withName(String newName) {
        return new ModelFolder(id, parentId, archiId, newName, folderType, sortOrder, rawXml);
    }

    /** Та же папка под другим родителем и на другой позиции. */
    public ModelFolder movedTo(FolderId newParent, SortOrder newOrder) {
        return new ModelFolder(id, Objects.requireNonNull(newParent, "newParent"), archiId, name, folderType,
                newOrder, rawXml);
    }
}
