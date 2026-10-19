package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.UUID;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.View;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEdgeId;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewEndpoint;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.ViewNodeId;

/** Конец ребра по ключу из запроса. */
public final class ViewEndpoints {

    private ViewEndpoints() {
    }

    /** Узел, если такой узел есть на представлении, иначе ребро. */
    public static ViewEndpoint of(View view, UUID id) {
        return view.nodes().contains(ViewNodeId.of(id)) ? ViewNodeId.of(id) : ViewEdgeId.of(id);
    }
}
