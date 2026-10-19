package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import java.util.List;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ListModelsQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ValidateModelQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelTreeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ValidationFindingResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/**
 * Запросы модели — только чтение. Отдельно от команд: мост помощника видит этот порт
 * и не видит ни одного сценария записи.
 */
public interface ModelQueryApplicationService {

    /** Модели пространства, видимые автору. */
    List<ModelSummaryResponse> listModels(EditorIdentity actor, ListModelsQuery query);

    /** Дерево модели с размещениями. */
    ModelTreeResponse getModel(EditorIdentity actor, GetModelQuery query);

    /** Отчёт проверки модели. */
    List<ValidationFindingResponse> validateModel(EditorIdentity actor, ValidateModelQuery query);
}
