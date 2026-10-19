package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.UUID;

/**
 * Папка дерева модели.
 *
 * @param parentId   родитель; {@code null} — корневая папка
 * @param folderType тип корневой папки; {@code null} — вложенная
 */
public record FolderResponse(UUID id, UUID parentId, String archiId, String name, String folderType,
                             long sortOrder) {
}
