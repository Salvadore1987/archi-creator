package uz.salvadore.hamkorbank.archi.modeling.domain.view;

import java.util.UUID;

/** Конец ребра представления — узел или другое ребро того же представления. */
public sealed interface ViewEndpoint permits ViewNodeId, ViewEdgeId {

    UUID value();
}
