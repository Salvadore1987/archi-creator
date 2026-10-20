package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.VerifyModelRowsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ModelRowsMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderId;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId;
import uz.salvadore.hamkorbank.archi.modeling.domain.workspace.WorkspaceId;

/**
 * Собирается ли модель из строк: агрегаты строятся и проверяют свою структуру, но никуда
 * не пишутся. Вызывающий узнаёт об отказе до транзакции записи и отклоняет документ сам.
 */
public final class ModelRowsVerifyQueryHandler {

    private final ModelingKernel kernel;

    public ModelRowsVerifyQueryHandler(ModelingKernel kernel) {
        this.kernel = kernel;
    }

    /** Ничего не отдаёт; не собирается — отказ modeling с причиной. */
    public void handle(VerifyModelRowsQuery query) {
        ModelRowsMapper.content(query.rows(), new ModelRowsMapper.Identity(ModelId.of(kernel.uuids().next()),
                WorkspaceId.of(kernel.uuids().next()), "", kernel.now(), kernel.now(), 0),
                () -> FolderId.of(kernel.uuids().next()), kernel.archiIds()::next);
    }
}
