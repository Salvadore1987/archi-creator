package uz.salvadore.hamkorbank.archi.interchange.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.FindingId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportFinding;

class MethodologyCheckTest {

    private static final NoArgGenerator IDS = Generators.timeBasedEpochGenerator();

    @Test
    @DisplayName("Тип не в нотации ArchiMate — сведение «неизвестный тип», а не сбой проверки")
    void typeOutsideArchimateNotationIsReported() {
        ModelDocument document = new StaxArchiDocumentReader().read(new ByteArrayInputStream("""
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Тест" id="id-m" version="5.0.0">
                  <folder name="Other" id="id-fo" type="other">
                    <element xsi:type="canvas:CanvasModelBlock" name="Блок" id="id-block"/>
                    <element xsi:type="archimate:Location" name="Ташкент" id="id-loc"/>
                  </folder>
                  <folder name="Relations" id="id-fr" type="relations">
                    <element xsi:type="archimate:AssociationRelationship" id="id-r" source="id-block" target="id-loc"/>
                  </folder>
                </archimate:model>
                """.getBytes(StandardCharsets.UTF_8)));

        List<ImportFinding> findings = new MethodologyCheck().check(document, () -> new FindingId(IDS.generate()));

        assertEquals(List.of(MethodologyCheck.UNKNOWN_TYPE),
                findings.stream().map(ImportFinding::code).toList());
    }
}
