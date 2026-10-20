package uz.salvadore.hamkorbank.archi.interchange.application.port.in;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.query.ExportModelQuery;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ExportFileResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Выгрузка модели по зафиксированной версии: {@code .archimate}, каталог CSV, Open Exchange. */
public interface ExportApplicationService {

    /** Файл модели в запрошенном формате; формат не задан — {@code .archimate}. */
    ExportFileResponse exportModel(EditorIdentity actor, ExportModelQuery query);
}
