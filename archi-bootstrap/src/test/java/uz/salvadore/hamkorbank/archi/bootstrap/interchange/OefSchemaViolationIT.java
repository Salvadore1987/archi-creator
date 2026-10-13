package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;
import uz.salvadore.hamkorbank.archi.interchange.application.oef.OefSchema;

/** Отдельный контекст: вместо официальной схемы — схема, которой не удовлетворяет ни один OEF. */
class OefSchemaViolationIT extends ApiTest {

    @TestConfiguration
    static class RejectingSchema {

        @Bean
        @Primary
        OefSchema rejectingOefSchema() throws Exception {
            String xsd = """
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                      <xs:element name="nothing"/>
                    </xs:schema>
                    """;
            return OefSchema.of(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(new StreamSource(new StringReader(xsd))));
        }
    }

    @Test
    @DisplayName("FR-43: результат, не прошедший XSD, не отдаётся — 500 IXC_OEF_SCHEMA_VIOLATION без подробностей схемы")
    void invalidResultIsServerError() throws Exception {
        String model = read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "styled.archimate", "application/xml",
                                Fixtures.fixture("styled_objects")))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");

        mvc.perform(get("/api/v1/models/" + model + "/export?fmt=oef").with(viewer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("IXC_OEF_SCHEMA_VIOLATION"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("cvc-"))));
    }
}
