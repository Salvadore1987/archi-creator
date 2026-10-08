package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

/** Чем является объект файла с данным {@code xsi:type}. */
public enum ConceptKind {
    /** Элемент модели: {@code archimate:ApplicationComponent}. */
    ELEMENT,
    /** Связь модели: {@code archimate:ServingRelationship}. */
    RELATIONSHIP,
    /** Соединитель связей. Лежит в модели как элемент, но концом связи служит наравне с ними. */
    JUNCTION,
    /** Представление: {@code archimate:ArchimateDiagramModel}. */
    VIEW,
    /** Узел представления: {@code archimate:DiagramObject}, {@code archimate:Group}, {@code archimate:Note}. */
    VIEW_NODE,
    /** Ребро представления: {@code archimate:Connection}. */
    VIEW_EDGE
}
