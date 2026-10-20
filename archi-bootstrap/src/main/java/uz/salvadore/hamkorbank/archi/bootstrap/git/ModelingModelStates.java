package uz.salvadore.hamkorbank.archi.bootstrap.git;

import java.util.Map;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ModelStates;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ModelId;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.WorkspaceId;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.ReplaceModelContentCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetModelContentQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ReplaceModelContentResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.port.SnapshotUnreadableException;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ModelExchangeApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

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

    /**
     * Содержимое из коммита, которое не раскладывается в строки модели, — повреждённый документ,
     * а не дефект сервера: для modeling это снимок, для импорта — чужой файл.
     */
    @Override
    public Replaced replace(EditorIdentity actor, ModelId model, byte[] xml, String comment) {
        try {
            ReplaceModelContentResponse replaced = models.replaceModelContent(actor,
                    new ReplaceModelContentCommand(model.value(), xml, comment));
            return new Replaced(replaced.versionNo(), replaced.created(), replaced.opaqueObjects());
        } catch (SnapshotUnreadableException unreadable) {
            Object reason = unreadable.getCause() instanceof InterchangeDomainException cause ? cause.reason()
                    : unreadable.reason();
            InterchangeException corrupt = new InterchangeException(InterchangeCodes.CORRUPT_DOCUMENT,
                    Failure.UNPROCESSABLE, Message.of(InterchangeMessages.IMPORT_CONTENT_REJECTED, reason), Map.of());
            corrupt.initCause(unreadable);
            throw corrupt;
        }
    }
}
