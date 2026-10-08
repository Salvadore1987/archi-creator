package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/** Небольшие документы для тестов кодека: корень Archi вокруг переданного содержимого. */
final class Xml {

    static final String MODEL_ID = "id-9c7b5378a397030e3357cb9b";

    private Xml() {
    }

    static String model(String content) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Тест" id="%s" version="5.0.0">
                %s
                </archimate:model>
                """.formatted(MODEL_ID, content);
    }

    static ModelDocument read(String xml) {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
