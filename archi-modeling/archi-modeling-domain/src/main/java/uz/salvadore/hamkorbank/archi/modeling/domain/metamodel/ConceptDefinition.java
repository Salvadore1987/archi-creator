package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import java.util.Objects;

/**
 * Запись каталога: что за тип, какого слоя и с какой фазы поддержан.
 *
 * @param phase фаза, в которой тип становится редактируемым; {@code null} — фаза не назначена
 *              (так сейчас у {@code Location} и {@code Grouping}: они пока не отнесены ни к одной)
 */
public record ConceptDefinition(ArchiType type, ConceptKind kind, Layer layer, MetamodelPhase phase) {

    public ConceptDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(layer, "layer");
    }

    /** Редактируется ли тип в текущей фазе. Неподдержанный хранится как opaque. */
    public boolean supported() {
        return phase != null && phase.supported();
    }
}
