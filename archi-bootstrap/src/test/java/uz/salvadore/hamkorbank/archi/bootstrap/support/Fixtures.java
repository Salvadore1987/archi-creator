package uz.salvadore.hamkorbank.archi.bootstrap.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import uz.salvadore.hamkorbank.archi.bootstrap.roundtrip.RoundTripGoldenFileTest;

/** Фикстуры §9.1 и эталонная модель для интеграционных тестов. */
public final class Fixtures {

    public static final String REFERENCE = "docs/Hamkorbank_AS_IS_strict.archimate";
    public static final List<String> SYNTHETIC = List.of("capability_and_location", "nested_containment",
            "all_relationship_types", "styled_objects", "unknown_extension");

    private Fixtures() {
    }

    public static byte[] reference() {
        try {
            return Files.readAllBytes(RoundTripGoldenFileTest.repositoryFile(REFERENCE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] fixture(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name + ".archimate")) {
            if (in == null) {
                throw new IllegalStateException("нет фикстуры " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Маленькая модель с нарушением матрицы: Assignment от компонента к актору не разрешён. */
    public static byte[] withMatrixViolation() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Нарушение" id="id-m1" version="5.0.0">
                  <folder name="Strategy" id="id-f1" type="strategy"/>
                  <folder name="Business" id="id-f2" type="business">
                    <element xsi:type="archimate:BusinessActor" name="Клиент" id="id-e1"/>
                  </folder>
                  <folder name="Application" id="id-f3" type="application">
                    <element xsi:type="archimate:ApplicationComponent" name="АБС" id="id-e2"/>
                  </folder>
                  <folder name="Technology &amp; Physical" id="id-f4" type="technology"/>
                  <folder name="Motivation" id="id-f5" type="motivation"/>
                  <folder name="Implementation &amp; Migration" id="id-f6" type="implementation_migration"/>
                  <folder name="Other" id="id-f7" type="other"/>
                  <folder name="Relations" id="id-f8" type="relations">
                    <element xsi:type="archimate:AssignmentRelationship" id="id-r1" source="id-e2" target="id-e1"/>
                  </folder>
                  <folder name="Views" id="id-f9" type="diagrams"/>
                </archimate:model>
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
