package uz.salvadore.hamkorbank.archi.modeling.application.handler;

import java.util.List;
import java.util.stream.Stream;
import uz.salvadore.hamkorbank.archi.modeling.application.dto.result.ElementTypeResponse;
import uz.salvadore.hamkorbank.archi.modeling.application.mapper.MetamodelDataMapper;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ArchiTypeRegistry;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.ConceptKind;

/** Каталог типов для палитры: элементы и соединители с отметкой, правятся ли они в текущей фазе. */
public final class ElementTypeListQueryHandler {

    /** Все типы элементов и соединителей ArchiMate 3.2 в порядке каталога. */
    public List<ElementTypeResponse> handle() {
        ArchiTypeRegistry registry = ArchiTypeRegistry.archimate32();
        return Stream.of(ConceptKind.ELEMENT, ConceptKind.JUNCTION)
                .flatMap(kind -> registry.concepts(kind).stream())
                .map(MetamodelDataMapper::elementType)
                .toList();
    }
}
