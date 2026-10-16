package uz.salvadore.hamkorbank.archi.bootstrap.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.SvgWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/**
 * Экспорт картинок на настоящих шрифте, нотации и растеризаторе: все представления
 * эталонной модели печатаются, PNG нужного размера, вшитый шрифт читается.
 */
class ViewImageRenderingTest {

    private static final Path REFERENCE = Path.of("../docs/Hamkorbank_AS_IS_strict.archimate");
    private static final Notation NOTATION = SharedNotation.load(InterTypesetter.FAMILY);
    private static final InterTypesetter TYPESETTER = new InterTypesetter();
    private static final SvgWriter WRITER = new SvgWriter(NOTATION, TYPESETTER, message -> message.key());

    @Test
    @DisplayName("UI-019: нотация сервера — из тех же файлов, что у канвы")
    void notationComesFromSharedFiles() {
        assertThat(NOTATION.shapes()).containsKeys("archimate:ApplicationComponent", "archimate:Group");
        assertThat(NOTATION.layer("application").fill()).isEqualTo("#E3F2FD");
        assertThat(NOTATION.value("paper")).isEqualTo("#FAF9F5");
        assertThat(NOTATION.icons()).containsKey("i-component");
        NOTATION.shapes().values().stream().filter(entry -> entry.icon() != null)
                .forEach(entry -> assertThat(NOTATION.icons()).containsKey(entry.icon().spriteId()));
    }

    @Test
    @DisplayName("FR-41: каждое представление эталона печатается в SVG и PNG нужного размера")
    void everyReferenceViewRenders() throws IOException {
        ModelDocument document = reference();
        Path out = Path.of("target/export-preview");
        Files.createDirectories(out);
        for (DocumentNode view : document.views()) {
            SvgWriter.Rendered outline = WRITER.write(document, view, new SvgWriter.Options(true, false));
            byte[] png = new BatikRasterizer().png(outline.svg(), (int) outline.width() * 2,
                    (int) outline.height() * 2);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertThat(image.getWidth()).isEqualTo((int) outline.width() * 2);
            assertThat(image.getHeight()).isEqualTo((int) outline.height() * 2);
            String name = view.archiId().value();
            Files.write(out.resolve(name + ".png"), png);
            Files.write(out.resolve(name + ".svg"),
                    WRITER.write(document, view, new SvgWriter.Options(false, false)).svg());
        }
    }

    @Test
    @DisplayName("FR-41: вшитое подмножество шрифта — годный TrueType с нужными символами")
    void embeddedFontSubsetIsValid() throws IOException {
        ModelDocument document = reference();
        String svg = new String(WRITER.write(document, document.views().getFirst(),
                new SvgWriter.Options(false, false)).svg(), StandardCharsets.UTF_8);
        Matcher font = Pattern.compile("base64,([A-Za-z0-9+/=]+)\\)").matcher(svg);
        assertThat(font.find()).isTrue();
        byte[] bytes = Base64.getDecoder().decode(font.group(1));
        assertThat(bytes.length).isLessThan(60_000);
        try (TrueTypeFont subset = new TTFParser().parse(new RandomAccessReadBuffer(bytes))) {
            assertThat(subset.getUnicodeCmapLookup().getGlyphId('К')).isPositive();
            assertThat(subset.getNaming()).isNotNull();
        }
    }

    private static ModelDocument reference() throws IOException {
        return new StaxArchiDocumentReader().read(new ByteArrayInputStream(Files.readAllBytes(REFERENCE)));
    }
}
