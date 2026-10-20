package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.uuid.impl.UUIDUtil;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.interchange.application.port.ImportSessionRepository;
import uz.salvadore.hamkorbank.archi.interchange.domain.identity.ImportSessionId;
import uz.salvadore.hamkorbank.archi.interchange.domain.importing.ImportStatus;

/** Файл с неверным деревом — отказ с кодом и сессия в журнале, а не {@code 500} без следа. */
class ImportStructureIT extends ApiTest {

    @Autowired
    ImportSessionRepository sessions;

    @Test
    @DisplayName("INV-IXC-007: корневая папка без типа — 400 с находкой, сессия в журнале, повтор ключа — она же")
    void rootFolderWithoutTypeIsCorruptFile() throws Exception {
        byte[] file = model("<folder name=\"Business\" id=\"id-f1\"/>");

        MvcResult first = upload(file, "structure-1");
        MvcResult replay = upload(file, "structure-1");

        String session = read(first, "$.sessionId");
        assertEquals(session, read(replay, "$.sessionId"));
        assertEquals("IXC_MISSING_ATTRIBUTE", read(first, "$.findings[0].code"));
        assertEquals(ImportStatus.REJECTED,
                sessions.find(new ImportSessionId(UUIDUtil.uuid(session))).orElseThrow().status());
    }

    @Test
    @DisplayName("INV-IXC-007: узел представления в папке — 400 с местом узла")
    void misplacedNodeIsCorruptFile() throws Exception {
        MvcResult result = upload(model("""
                <folder name="Business" id="id-f1" type="business">
                  <child xsi:type="archimate:DiagramObject" id="id-n1"/>
                </folder>"""), "structure-2");

        assertEquals("IXC_MISPLACED_NODE", read(result, "$.findings[0].code"));
        assertEquals("id-n1", read(result, "$.findings[0].archiId"));
    }

    @Test
    @DisplayName("INV-IXC-007: модель из разобранного файла не собирается — 400, а не 500")
    void unbuildableModelIsCorruptFile() throws Exception {
        MvcResult result = upload(model("<folder name=\"Business\" id=\"id-f1\" type=\"неизвестный\"/>"),
                "structure-3");

        assertEquals("IXC_CORRUPT_DOCUMENT", read(result, "$.findings[0].code"));
    }

    private MvcResult upload(byte[] file, String key) throws Exception {
        return mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "broken.archimate", "application/xml", file))
                        .header("Idempotency-Key", key)
                        .with(architect()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IXC_CORRUPT_DOCUMENT"))
                .andReturn();
    }

    private static byte[] model(String content) {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <archimate:model xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
                xmlns:archimate="http://www.archimatetool.com/archimate" name="Тест" id="id-m" version="5.0.0">
                """ + content + "\n</archimate:model>\n").getBytes(StandardCharsets.UTF_8);
    }
}
