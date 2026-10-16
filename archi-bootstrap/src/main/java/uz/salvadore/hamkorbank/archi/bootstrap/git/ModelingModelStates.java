package uz.salvadore.hamkorbank.archi.bootstrap.git;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelImportService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Содержимое модели для импорта состояния: перевод идентификаторов двух контекстов и ничего больше. */
public final class ModelingModelStates implements ModelStates {

    private final ModelImportService models;

    public ModelingModelStates(ModelImportService models) {
        this.models = models;
    }

    @Override
    public Current current(EditorIdentity actor, ModelId model) {
        ModelImportService.CurrentContent current = models.current(actor, modeling(model));
        return new Current(new WorkspaceId(current.header().workspaceId().value()), current.header().name(),
                Optional.ofNullable(current.versionNo()), current.xml());
    }

    @Override
    public Replaced replace(EditorIdentity actor, ModelId model, byte[] xml, String comment) {
        ModelImportService.Replaced replaced = models.replace(actor, modeling(model), xml, comment);
        return new Replaced(replaced.version().versionNo(), replaced.created(), replaced.opaqueObjects());
    }

    private static uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId modeling(ModelId model) {
        return uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelId.of(model.value());
    }
}
