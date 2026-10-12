package uz.salvadore.hamkorbank.archi.bootstrap.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Rasterizer;

/**
 * PNG через Apache Batik — в JVM, без нативных зависимостей в образе. Получает SVG
 * с подписями в кривых, поэтому от шрифтов системы не зависит.
 */
public final class BatikRasterizer implements Rasterizer {

    @Override
    public byte[] png(byte[] svg, int width, int height) {
        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, (float) width);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, (float) height);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_EXECUTE_ONLOAD, Boolean.FALSE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            transcoder.transcode(new TranscoderInput(new ByteArrayInputStream(svg)), new TranscoderOutput(out));
        } catch (TranscoderException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }
}
