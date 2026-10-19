package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.List;
import java.util.UUID;

/** Перенести объекты дерева в папку. */
public record MoveTreeItemsCommand(UUID modelId, UUID targetFolderId, List<UUID> itemIds) {

    public MoveTreeItemsCommand {
        itemIds = List.copyOf(itemIds);
    }
}
