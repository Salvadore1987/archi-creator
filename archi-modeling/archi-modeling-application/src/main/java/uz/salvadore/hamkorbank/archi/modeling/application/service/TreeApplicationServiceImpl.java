package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateFolderCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.MoveTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RenameTreeItemCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.FolderResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.FolderCreateCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.TreeItemRenameCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.TreeItemsDeleteCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.TreeItemsMoveCommandHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.TreeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Дерево модели: делегирует в handler'ы сценариев. */
public final class TreeApplicationServiceImpl implements TreeApplicationService {

    private final FolderCreateCommandHandler createFolder;
    private final TreeItemRenameCommandHandler rename;
    private final TreeItemsMoveCommandHandler move;
    private final TreeItemsDeleteCommandHandler delete;

    public TreeApplicationServiceImpl(FolderCreateCommandHandler createFolder, TreeItemRenameCommandHandler rename,
                                      TreeItemsMoveCommandHandler move, TreeItemsDeleteCommandHandler delete) {
        this.createFolder = createFolder;
        this.rename = rename;
        this.move = move;
        this.delete = delete;
    }

    @Override
    public FolderResponse createFolder(EditorIdentity actor, CreateFolderCommand command) {
        return createFolder.handle(actor, command);
    }

    @Override
    public void renameTreeItem(EditorIdentity actor, RenameTreeItemCommand command) {
        rename.handle(actor, command);
    }

    @Override
    public void moveTreeItems(EditorIdentity actor, MoveTreeItemsCommand command) {
        move.handle(actor, command);
    }

    @Override
    public void deleteTreeItems(EditorIdentity actor, DeleteTreeItemsCommand command) {
        delete.handle(actor, command);
    }
}
