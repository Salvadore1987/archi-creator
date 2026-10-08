package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import java.util.Objects;
import java.util.Optional;

/**
 * Запись каталога: что за тип, какого слоя и с какой фазы поддержан.
 *
 * @param phase фаза, в которой тип становится редактируемым; пусто — фаза не назначена
 *              (так сейчас у {@code Location} и {@code Grouping}: спека не относит их ни к одной)
 */
public record ConceptDefinition(ArchiType type, ConceptKind kind, Layer layer, Optional<MetamodelPhase> phase) {

    public ConceptDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(phase, "phase");
    }

    /** Редактируется ли тип в текущей фазе. Неподдержанный хранится как opaque (FR-03). */
    public boolean supported() {
        return phase.filter(p -> p == MetamodelPhase.PHASE_1).isPresent();
    }
}
