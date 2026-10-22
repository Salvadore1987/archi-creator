package uz.salvadore.hamkorbank.archi.modeling.application.dto.result;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Папка дерева модели.
 *
 * @param parentId   родитель; {@code null} — корневая папка
 * @param folderType тип корневой папки; {@code null} — вложенная
 */
public record FolderResponse(UUID id, @Nullable UUID parentId, String archiId, String name, @Nullable String folderType,
                             long sortOrder) {
}
