package uz.salvadore.hamkorbank.archi.advisor.application.port;

import uz.salvadore.hamkorbank.archi.advisor.domain.common.Requester;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ModelGraph;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.advisor.domain.identity.WorkspaceId;

/**
 * Единственный путь помощника к модели — чтение. Порта записи в modeling у помощника
 * нет. Доступ решает modeling: модель, скрытая от автора списком доступа, не находится.
 */
public interface ModelContextReader {

    /** Снимок модели. Нет модели или она скрыта — отказ «не найдено». */
    ReadModel read(Requester requester, ModelId modelId);

    /**
     * @param deleted модель в корзине: обращаться к помощнику по ней не о чем
     */
    record ReadModel(WorkspaceId workspaceId, boolean deleted, ModelGraph graph) {
    }
}
