package uz.salvadore.hamkorbank.archi.bootstrap.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.SvgWriter;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.StaxArchiDocumentReader;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/**
 * Серверная половина сверки канвы и экспорта. PNG представлений эталона лежат
 * в репозитории рядом с визуальными тестами фронтенда — их сравнивает с канвой
 * {@code visual/export-parity.spec.ts}. Этот тест держит, что лежащие PNG — то, что
 * сервер печатает сейчас: правка писателя без пересъёмки эталонов роняет сборку.
 *
 * <p>Пересъёмка: {@code ./mvnw -P '!frontend' -pl archi-bootstrap -am test
 * -Dtest=ExportParityFixturesTest -Dexport.parity.update=true -Dsurefire.failIfNoSpecifiedTests=false}.
 */
class ExportParityFixturesTest {

    /** Представления эталона, которые целиком помещаются на экран при 100 %. */
    static final List<String> VIEWS = List.of("id-f11fbcb80991efa5a637e786", "id-0071f2a029a55a5baf3764d7",
            "id-c249b4ce7da44d6b354f9e9d", "id-8a09711388778e059c6d5fb9", "id-56477524f08510e0e54a5d04");

    private static final Path REFERENCE = Path.of("../docs/Hamkorbank_AS_IS_strict.archimate");
    private static final Path FIXTURES = Path.of("src/main/frontend/visual/export-parity");
    /** Канал различается больше чем на столько — пиксель разный; таких допускается доля. */
    private static final int CHANNEL_TOLERANCE = 24;
    private static final double PIXEL_RATIO = 0.002;

    @Test
    @DisplayName("FR-42: эталоны сверки с канвой — то, что сервер печатает сейчас")
    void parityFixturesMatchWriter() throws IOException {
        boolean update = Boolean.getBoolean("export.parity.update");
        ModelDocument document = new StaxArchiDocumentReader()
                .read(new ByteArrayInputStream(Files.readAllBytes(REFERENCE)));
        Notation notation = SharedNotation.load(InterTypesetter.FAMILY);
        SvgWriter writer = new SvgWriter(notation, new InterTypesetter(), message -> message.key());
        Files.createDirectories(FIXTURES);
        for (String id : VIEWS) {
            DocumentNode view = document.find(ArchiId.of(id)).orElseThrow();
            SvgWriter.Rendered rendered = writer.write(document, view, new SvgWriter.Options(true, false));
            byte[] png = new BatikRasterizer().png(rendered.svg(), (int) Math.ceil(rendered.width()),
                    (int) Math.ceil(rendered.height()));
            String frame = String.format(Locale.ROOT, "{\"x\":%s,\"y\":%s,\"width\":%s,\"height\":%s}%n",
                    (long) rendered.x(), (long) rendered.y(), (long) rendered.width(), (long) rendered.height());
            Path image = FIXTURES.resolve(id + ".png");
            Path box = FIXTURES.resolve(id + ".json");
            if (update) {
                Files.write(image, png);
                Files.writeString(box, frame, StandardCharsets.UTF_8);
                continue;
            }
            assertThat(image).as("эталон %s не снят — пересъёмка в javadoc теста", id).exists();
            assertThat(Files.readString(box, StandardCharsets.UTF_8)).as(id).isEqualTo(frame);
            assertThat(differingRatio(ImageIO.read(image.toFile()), ImageIO.read(new ByteArrayInputStream(png))))
                    .as("эталон %s разошёлся с писателем — переснять", id).isLessThanOrEqualTo(PIXEL_RATIO);
        }
    }

    private static double differingRatio(BufferedImage expected, BufferedImage actual) {
        assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).isEqualTo(expected.getHeight());
        long differing = 0;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int a = expected.getRGB(x, y);
                int b = actual.getRGB(x, y);
                for (int shift = 0; shift <= 24; shift += 8) {
                    if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > CHANNEL_TOLERANCE) {
                        differing++;
                        break;
                    }
                }
            }
        }
        return (double) differing / ((long) expected.getWidth() * expected.getHeight());
    }
}
