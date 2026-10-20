package uz.salvadore.hamkorbank.archi.interchange.application.service;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportAllViewImagesQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportViewImageQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ExportFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.AllViewImagesExportQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ViewImageExportQueryHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ViewImageApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Картинки представлений: делегирует в handler'ы сценариев. */
public final class ViewImageApplicationServiceImpl implements ViewImageApplicationService {

    private final ViewImageExportQueryHandler one;
    private final AllViewImagesExportQueryHandler all;

    public ViewImageApplicationServiceImpl(ViewImageExportQueryHandler one, AllViewImagesExportQueryHandler all) {
        this.one = one;
        this.all = all;
    }

    @Override
    public ExportFileResponse exportViewImage(EditorIdentity actor, ExportViewImageQuery query) {
        return one.handle(actor, query);
    }

    @Override
    public ExportFileResponse exportAllViewImages(EditorIdentity actor, ExportAllViewImagesQuery query) {
        return all.handle(actor, query);
    }
}
