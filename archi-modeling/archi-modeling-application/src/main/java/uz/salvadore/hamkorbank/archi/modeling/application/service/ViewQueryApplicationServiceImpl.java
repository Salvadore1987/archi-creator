package uz.salvadore.hamkorbank.archi.modeling.application.service;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.GetViewQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.query.ProposeViewLayoutQuery;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.LayoutProposalResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ViewResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewGetQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.handler.ViewLayoutProposeQueryHandler;
import uz.salvadore.hamkorbank.archi.modeling.application.port.in.ViewQueryApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Чтение представлений: делегирует в handler'ы запросов. */
public final class ViewQueryApplicationServiceImpl implements ViewQueryApplicationService {

    private final ViewGetQueryHandler get;
    private final ViewLayoutProposeQueryHandler proposeLayout;

    public ViewQueryApplicationServiceImpl(ViewGetQueryHandler get, ViewLayoutProposeQueryHandler proposeLayout) {
        this.get = get;
        this.proposeLayout = proposeLayout;
    }

    @Override
    public ViewResponse getView(EditorIdentity actor, GetViewQuery query) {
        return get.handle(actor, query);
    }

    @Override
    public LayoutProposalResponse proposeLayout(EditorIdentity actor, ProposeViewLayoutQuery query) {
        return proposeLayout.handle(actor, query);
    }
}
