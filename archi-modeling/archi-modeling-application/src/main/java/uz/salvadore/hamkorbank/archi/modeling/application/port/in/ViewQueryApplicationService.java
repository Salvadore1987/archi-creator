package uz.salvadore.hamkorbank.archi.modeling.application.port.in;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetViewQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ProposeViewLayoutQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LayoutProposalResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Чтение представлений: открыть и предложить авторазметку; база не меняется. */
public interface ViewQueryApplicationService {

    /** Представление целиком. */
    ViewResponse getView(EditorIdentity actor, GetViewQuery query);

    /** Предложение авторазметки по графу из браузера. */
    LayoutProposalResponse proposeLayout(EditorIdentity actor, ProposeViewLayoutQuery query);
}
