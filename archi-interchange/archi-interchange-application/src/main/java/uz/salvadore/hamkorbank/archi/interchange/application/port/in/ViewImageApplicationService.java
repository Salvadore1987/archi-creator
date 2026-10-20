package uz.salvadore.hamkorbank.archi.interchange.application.port.in;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportAllViewImagesQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportViewImageQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ExportFileResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Картинки представлений в SVG и PNG из зафиксированной версии. */
public interface ViewImageApplicationService {

    /** Картинка одного представления; его нет в запрошенной версии — отказ. */
    ExportFileResponse exportViewImage(EditorIdentity actor, ExportViewImageQuery query);

    /** Все представления модели одним архивом из одного снимка, с отчётом о потерях. */
    ExportFileResponse exportAllViewImages(EditorIdentity actor, ExportAllViewImagesQuery query);
}
