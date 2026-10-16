package uz.salvadore.hamkorbank.archi.interchange.domain.residue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;

class NodeResidueTest {

    @Test
    @DisplayName("ADR-0017: остаток переживает запись и чтение без потерь, включая переводы строк")
    void residueSurvivesEncodeDecode() {
        NodeResidue residue = new NodeResidue(
                List.of(ResidueAttribute.placeholder("xsi:type"), ResidueAttribute.literal("vendor:owner", "Розница"),
                        ResidueAttribute.literal("labelExpression", "${name}\n${property:owner}\r\tконец"),
                        ResidueAttribute.literal("empty", "")),
                List.of(new ResidueItem.Slot(1000, "documentation", List.of()),
                        new ResidueItem.Slot(2000, "bounds", List.of(ResidueAttribute.placeholder("x"),
                                ResidueAttribute.literal("extra", "1"))),
                        new ResidueItem.Value(3000, "content", List.of(), "  Платить по QR\n  "),
                        new ResidueItem.Value(3500, "purpose", List.of(), ""),
                        new ResidueItem.Fragment(4000,
                                "<vendor:step order=\"1\">очистка &amp; дедупликация</vendor:step>")));

        NodeResidue decoded = NodeResidue.decode(residue.encode());

        assertEquals(residue, decoded);
        assertTrue(NodeResidue.decode(NodeResidue.EMPTY.encode()).empty());
    }

    @Test
    @DisplayName("Повреждённый остаток из хранилища — один отказ «остаток повреждён», какой бы ни была порча")
    void corruptedResidueIsOneDomainFailure() {
        for (String broken : List.of("<r><s/></r>", "<r><a/></r>", "<r><x/></r>", "<r>")) {
            InterchangeDomainException e = assertThrows(InterchangeDomainException.class,
                    () -> NodeResidue.decode(broken), broken);
            assertEquals(InterchangeCodes.RESIDUE_CORRUPTED, e.code(), broken);
        }
    }
}
