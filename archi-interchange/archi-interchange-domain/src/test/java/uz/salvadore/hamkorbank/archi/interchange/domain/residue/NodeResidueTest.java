package uz.salvadore.hamkorbank.archi.interchange.domain.residue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeDomainException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InvalidValueException;

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
        for (String broken : List.of("<r><s/></r>", "<r><a/></r>", "<r><x/></r>", "<r>",
                "<r><s o=\"x\" t=\"a\"/></r>", "<r><v o=\"1\"/></r>", "<r><f o=\"1\"></f></r>")) {
            InterchangeDomainException e = assertThrows(InterchangeDomainException.class,
                    () -> NodeResidue.decode(broken), broken);
            assertEquals(InterchangeCodes.RESIDUE_CORRUPTED, e.code(), broken);
        }
    }

    @Test
    @DisplayName("остаток с чужим корневым элементом — отказ «остаток повреждён» с причиной в записи")
    void residueWithForeignRootIsCorrupted() {
        InterchangeDomainException e = assertThrows(InterchangeDomainException.class,
                () -> NodeResidue.decode("<q><s o=\"1\" t=\"bounds\"/></q>"));

        assertEquals(InterchangeCodes.RESIDUE_CORRUPTED, e.code());
        assertEquals(InterchangeMessages.RESIDUE_CORRUPTED, e.reason().key());
        InvalidValueException cause = assertInstanceOf(InvalidValueException.class, e.getCause());
        assertEquals(InterchangeMessages.RESIDUE_RECORD_EXPECTED, cause.reason().key());
        assertEquals(List.of("r"), cause.reason().args());
    }

    @Test
    @DisplayName("значение остатка хранит атрибуты целиком: заглушка столбца в нём недопустима")
    void valueWithTypedAttributeIsRejected() {
        List<ResidueAttribute> attributes = List.of(ResidueAttribute.literal("name", "a"),
                ResidueAttribute.placeholder("key"));

        InvalidValueException e = assertThrows(InvalidValueException.class,
                () -> new ResidueItem.Value(1000, "property", attributes, null));

        assertEquals(InterchangeCodes.INVALID_REQUEST, e.code());
        assertEquals(InterchangeMessages.UNTYPED_VALUE_LITERAL, e.reason().key());
    }

    @Test
    @DisplayName("фрагмент остатка — элемент XML, атрибут остатка — с именем XML")
    void residueValuesCheckThemselves() {
        assertThrows(InvalidValueException.class, () -> new ResidueItem.Fragment(0, "text"));
        assertThrows(InvalidValueException.class, () -> new ResidueItem.Fragment(0, "<!-- c -->"));
        assertThrows(InvalidValueException.class, () -> ResidueAttribute.literal("na me", "v"));
    }
}
