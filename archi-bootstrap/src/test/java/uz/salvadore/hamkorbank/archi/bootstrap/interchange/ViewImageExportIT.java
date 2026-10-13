package uz.salvadore.hamkorbank.archi.bootstrap.interchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import uz.salvadore.hamkorbank.archi.bootstrap.support.ApiTest;
import uz.salvadore.hamkorbank.archi.bootstrap.support.Fixtures;

class ViewImageExportIT extends ApiTest {

    @Test
    @DisplayName("FR-41, INV-IXC-006: VIEWER получает SVG и PNG представления; отчёт о потерях — в заголовке")
    void viewerExportsViewImage() throws Exception {
        String model = imported();
        String view = firstView(model);

        MockHttpServletResponse response = mvc.perform(get("/api/v1/views/" + view + "/export").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(header().string("X-Archi-Version-No", "1"))
                .andReturn().getResponse();
        // Шрифты Arial и Consolas из файла на сервере заменены Inter — это потери картинки.
        assertThat(Integer.parseInt(response.getHeader("X-Archi-Loss-Count"))).isGreaterThanOrEqualTo(2);
        String svg = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(svg).startsWith("<?xml").contains("<svg ", "@font-face", ">АБС<");

        String outline = new String(mvc.perform(get("/api/v1/views/" + view + "/export?outline=true&background=transparent")
                        .with(viewer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(outline).doesNotContain("<text", "@font-face");

        byte[] png = mvc.perform(get("/api/v1/views/" + view + "/export?fmt=png&scale=2").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        String viewBox = svg.replaceAll("(?s).*viewBox=\"([^\"]+)\".*", "$1");
        String[] box = viewBox.split(" ");
        assertThat(image.getWidth()).isEqualTo((int) Math.ceil(Double.parseDouble(box[2]) * 2));
        assertThat(image.getHeight()).isEqualTo((int) Math.ceil(Double.parseDouble(box[3]) * 2));
    }

    @Test
    @DisplayName("UC-IXC-004: плотность и фон вне списка — 422 с кодом; формат не картинка — IXC_UNKNOWN_FORMAT")
    void invalidOptionsAreRejected() throws Exception {
        String view = firstView(imported());
        mvc.perform(get("/api/v1/views/" + view + "/export?fmt=png&scale=3").with(viewer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IXC_INVALID_EXPORT_OPTION"));
        mvc.perform(get("/api/v1/views/" + view + "/export?background=black").with(viewer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IXC_INVALID_EXPORT_OPTION"));
        mvc.perform(get("/api/v1/views/" + view + "/export?fmt=pdf").with(viewer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IXC_UNKNOWN_FORMAT"));
    }

    @Test
    @DisplayName("UC-IXC-004: PNG стороной больше 16 000 px — отказ с наибольшей допустимой плотностью; SVG отдаётся")
    void oversizedPngIsRejected() throws Exception {
        String model = imported();
        String view = firstView(model);
        mvc.perform(post("/api/v1/models/" + model + "/lock").with(architect())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/views/" + view + "/nodes").with(architect())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"NOTE\",\"content\":\"далеко\",\"x\":5000,\"y\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/models/" + model + "/versions").with(architect())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"широкое\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/views/" + view + "/export?fmt=png&scale=4").with(viewer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IXC_IMAGE_TOO_LARGE"))
                .andExpect(jsonPath("$.maxScale").value(2));
        mvc.perform(get("/api/v1/views/" + view + "/export?fmt=svg").with(viewer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/views/" + view + "/export?fmt=png&scale=2").with(viewer()))
                .andExpect(status().isOk());
    }

    private String imported() throws Exception {
        return read(mvc.perform(multipart("/api/v1/models/import")
                        .file(new MockMultipartFile("file", "styled.archimate", "application/xml",
                                Fixtures.fixture("styled_objects")))
                        .with(architect()))
                .andExpect(status().isCreated()).andReturn(), "$.modelId");
    }

    private String firstView(String model) throws Exception {
        List<String> views = read(mvc.perform(get("/api/v1/models/" + model).with(viewer())).andReturn(),
                "$.views[*].id");
        return views.getFirst();
    }
}
