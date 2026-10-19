package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import uz.salvadore.hamkorbank.archi.modeling.application.access.Operation;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.ViewDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.application.service.ModelingKernel;
import uz.salvadore.hamkorbank.archi.modeling.application.service.RequestedIds;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingDomainException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ArchitectureModel.Created;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ElementId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNode;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeKind;

/**
 * Узел любого вида. Уже размещённый элемент возвращает существующий узел: идентификаторы
 * из команды тогда не проверяются и не применяются. Группа и заметка создаются всегда.
 * Незаданный размер — размер Archi для вида узла.
 */
public final class ViewNodePlaceCommandHandler {

    /** Поле запроса по имени в JSON — в сообщении о том, чего не хватает. */
    private static final String ELEMENT_ID = "elementId";

    private final ModelingKernel kernel;
    private final ViewLookup lookup;

    public ViewNodePlaceCommandHandler(ModelingKernel kernel, ViewLookup lookup) {
        this.kernel = kernel;
        this.lookup = lookup;
    }

    /** Новый узел или узел уже размещённого элемента. */
    public PlaceViewNodeResponse handle(EditorIdentity actor, PlaceViewNodeCommand command) {
        return kernel.run(Operation.SAVE_VIEW_LAYOUT, actor, () -> {
            ViewNodeKind kind = kind(command.kind());
            Bounds bounds = Bounds.ofNew(kind, command.x(), command.y(), command.width(), command.height());
            ViewNodeId parentId = command.parentId() == null ? null : ViewNodeId.of(command.parentId());
            RequestedIds ids = RequestedIds.of(command.id(), command.archiId());
            if (kind == ViewNodeKind.DIAGRAM_OBJECT && command.elementId() == null) {
                throw ModelingDomainException.invalid(Message.of(ModelingMessages.FIELD_MISSING, ELEMENT_ID));
            }
            if (kind != ViewNodeKind.DIAGRAM_OBJECT && command.elementId() != null) {
                throw ModelingDomainException.invalid(Message.of(ModelingMessages.FIELD_NOT_FOR_KIND, ELEMENT_ID,
                        kind));
            }
            Created<ViewNode> node = kernel.unitOfWork().write(() -> {
                View view = lookup.view(command.viewId());
                ArchitectureModel model = kernel.writableModel(view.modelId(), actor);
                Created<ViewNode> placed = kind == ViewNodeKind.DIAGRAM_OBJECT
                        ? view.placeElement(model, ElementId.of(command.elementId()), bounds, parentId,
                                () -> ViewNodeId.of(kernel.newId(ids)), () -> kernel.newArchiId(model, view, ids))
                        : new Created<>(view.placeAnnotation(kind, command.label(), command.content(), bounds,
                                parentId, ViewNodeId.of(kernel.newId(ids)), kernel.newArchiId(model, view, ids)),
                                true);
                kernel.views().save(view);
                return placed;
            });
            return new PlaceViewNodeResponse(ViewDataMapper.node(node.value()), node.created());
        });
    }

    /** Вид узла по имени из контракта; не задан — узел элемента. */
    private static ViewNodeKind kind(String value) {
        if (value == null) {
            return ViewNodeKind.DIAGRAM_OBJECT;
        }
        try {
            return ViewNodeKind.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.NODE_KIND_REQUESTED, value));
        }
    }
}
