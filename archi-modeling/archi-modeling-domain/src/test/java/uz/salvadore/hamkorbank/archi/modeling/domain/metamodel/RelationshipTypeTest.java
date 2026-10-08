package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RelationshipTypeTest {

    @Test
    @DisplayName("FR-09: одиннадцать типов связей в нотации файла Archi")
    void elevenRelationshipTypesInArchiNotation() {
        Set<String> types = Arrays.stream(RelationshipType.values())
                .map(r -> r.archiType().value())
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "archimate:CompositionRelationship", "archimate:AggregationRelationship",
                "archimate:AssignmentRelationship", "archimate:RealizationRelationship",
                "archimate:ServingRelationship", "archimate:AccessRelationship",
                "archimate:InfluenceRelationship", "archimate:TriggeringRelationship",
                "archimate:FlowRelationship", "archimate:SpecializationRelationship",
                "archimate:AssociationRelationship"), types);
    }

    @Test
    @DisplayName("тип связи восстанавливается из archiType, чужой тип — нет")
    void lookupByArchiType() {
        assertEquals(RelationshipType.SERVING,
                RelationshipType.fromArchiType(ArchiType.of("archimate:ServingRelationship")).orElseThrow());
        assertTrue(RelationshipType.fromArchiType(ArchiType.of("archimate:ApplicationComponent")).isEmpty());
    }

    @Test
    @DisplayName("буквы матрицы Archi не повторяются")
    void matrixKeysAreDistinct() {
        long distinct = Arrays.stream(RelationshipType.values()).map(RelationshipType::key).distinct().count();

        assertEquals(RelationshipType.values().length, distinct);
    }
}
