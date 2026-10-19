package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.CreateViewCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.EditViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.MoveViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.PlaceViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RemoveViewEdgeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.RemoveViewNodeCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.command.SaveViewLayoutCommand;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.PlaceViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewEdgeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewNodeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewSummaryResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Правка представлений: создание, узлы и рёбра, геометрия. */
public interface ViewApplicationService {

    /** Новое пустое представление; повтор ключа — то же. */
    ViewSummaryResponse createView(EditorIdentity actor, CreateViewCommand command);

    /** Узел элемента, группа или заметка; уже размещённый элемент — его узел. */
    PlaceViewNodeResponse placeNode(EditorIdentity actor, PlaceViewNodeCommand command);

    /** Ребро существующей связи; такое же уже есть — оно. */
    PlaceViewEdgeResponse placeEdge(EditorIdentity actor, PlaceViewEdgeCommand command);

    /** Вложить узел в контейнер или вынести в корень. */
    ViewNodeResponse moveNode(EditorIdentity actor, MoveViewNodeCommand command);

    /** Подпись, текст и собственный стиль узла. */
    ViewNodeResponse editNode(EditorIdentity actor, EditViewNodeCommand command);

    /** Собственный стиль ребра. */
    ViewEdgeResponse editEdge(EditorIdentity actor, EditViewEdgeCommand command);

    /** Убрать узел; элемент остаётся в модели. */
    void removeNode(EditorIdentity actor, RemoveViewNodeCommand command);

    /** Убрать ребро; связь остаётся в модели. */
    void removeEdge(EditorIdentity actor, RemoveViewEdgeCommand command);

    /** Геометрия одной транзакцией. */
    void saveLayout(EditorIdentity actor, SaveViewLayoutCommand command);
}
