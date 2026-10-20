package uz.salvadore.hamkorbank.archi.interchange.application.service;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportModelQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ExportFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ModelExportQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ExportApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Выгрузка модели: делегирует в handler сценария. */
public final class ExportApplicationServiceImpl implements ExportApplicationService {

    private final ModelExportQueryHandler export;

    public ExportApplicationServiceImpl(ModelExportQueryHandler export) {
        this.export = export;
    }

    @Override
    public ExportFileResponse exportModel(EditorIdentity actor, ExportModelQuery query) {
        return export.handle(actor, query);
    }
}
