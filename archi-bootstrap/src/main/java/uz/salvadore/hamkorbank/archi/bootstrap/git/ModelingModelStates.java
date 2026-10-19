package uz.salvadore.hamkorbank.archi.bootstrap.git;

import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceModelContentCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelContentQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ReplaceModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Содержимое модели для импорта состояния: перевод идентификаторов двух контекстов и ничего больше. */
public final class ModelingModelStates implements ModelStates {

    private final ModelExchangeApplicationService models;

    public ModelingModelStates(ModelExchangeApplicationService models) {
        this.models = models;
    }

    @Override
    public Current current(EditorIdentity actor, ModelId model) {
        ModelContentResponse current = models.getModelContent(actor, new GetModelContentQuery(model.value()));
        return new Current(new WorkspaceId(current.model().workspaceId()), current.model().name(),
                current.versionNo(), current.xml());
    }

    @Override
    public Replaced replace(EditorIdentity actor, ModelId model, byte[] xml, String comment) {
        ReplaceModelContentResponse replaced = models.replaceModelContent(actor,
                new ReplaceModelContentCommand(model.value(), xml, comment));
        return new Replaced(replaced.versionNo(), replaced.created(), replaced.opaqueObjects());
    }
}
