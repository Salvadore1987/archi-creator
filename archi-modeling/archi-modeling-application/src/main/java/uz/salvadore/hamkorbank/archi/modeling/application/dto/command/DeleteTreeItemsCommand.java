package uz.salvadore.hamkorbank.archi.modeling.application.dto.command;

import java.util.List;
import java.util.UUID;

/** Удалить объекты дерева одной транзакцией. */
public record DeleteTreeItemsCommand(UUID modelId, List<UUID> itemIds) {

    public DeleteTreeItemsCommand {
        itemIds = List.copyOf(itemIds);
    }
}
