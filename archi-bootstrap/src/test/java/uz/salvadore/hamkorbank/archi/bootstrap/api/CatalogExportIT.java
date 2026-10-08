package uz.salvadore.hamkorbank.archi.bootstrap.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

class CatalogExportIT extends ApiTest {

    @Test
    @DisplayName("FR-45: каталог в CSV доступен VIEWER — zip из двух файлов по зафиксированной версии")
    void viewerExportsCatalog() throws Exception {
        String model = read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "all.archimate", "application/xml",
                                Fixtures.fixture("all_relationship_types")))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");

        byte[] zip = mvc.perform(get("/api/v1/models/" + model + "/export?fmt=csv&sep=,").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("X-Archi-Version-No", "1"))
                .andReturn().getResponse().getContentAsByteArray();

        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            assertTrue(in.getNextEntry().getName().equals("elements.csv"));
            String elements = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(elements.startsWith("﻿id,type,name,layer,folder"), elements);
            assertTrue(in.getNextEntry().getName().equals("relations.csv"));
        }
        mvc.perform(get("/api/v1/models/" + model + "/export?fmt=oef").with(viewer()))
                .andExpect(status().isUnprocessableContent());
    }
}
