package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateFolderCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.DeleteTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.MoveTreeItemsCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RenameTreeItemCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.FolderResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Дерево модели: папки, переименование, перенос и групповое удаление. */
public interface TreeApplicationService {

    /** Новая папка; повтор ключа — та же папка. */
    FolderResponse createFolder(EditorIdentity actor, CreateFolderCommand command);

    /** Переименование папки, элемента, связи или представления. */
    void renameTreeItem(EditorIdentity actor, RenameTreeItemCommand command);

    /** Перенос в папку. */
    void moveTreeItems(EditorIdentity actor, MoveTreeItemsCommand command);

    /** Групповое удаление одной транзакцией. */
    void deleteTreeItems(EditorIdentity actor, DeleteTreeItemsCommand command);
}
