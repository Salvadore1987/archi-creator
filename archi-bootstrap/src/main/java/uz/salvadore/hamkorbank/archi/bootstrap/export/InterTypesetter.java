package uz.salvadore.hamkorbank.archi.bootstrap.export;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TTFSubsetter;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;

/**
 * Inter из classpath ({@code ui/fonts/}) — тот же выпуск, что подключает канва.
 * Системные шрифты не берутся: картинка не должна зависеть от образа.
 *
 * <p>Ширины и контуры — по {@link TextLayout} с кернингом и лигатурами, как у браузера;
 * подъём и спуск — из таблицы {@code hhea}, округлённые до пикселя, как округляет
 * их браузер при раскладке строки. Курсива в наборе нет — он наклоняется, как
 * браузер синтезирует отсутствующее начертание.
 */
public final class InterTypesetter implements Typesetter {

    public static final String FAMILY = "Inter";

    private static final Map<Integer, String> FILES = Map.of(400, "Regular", 500, "Medium", 600, "SemiBold",
            700, "Bold");
    /** Таблицы подмножества: без GSUB/GPOS — их ссылки на глифы после перенумерации были бы битыми. */
    private static final List<String> SUBSET_TABLES = List.of("head", "hhea", "hmtx", "maxp", "OS/2", "name", "post",
            "cmap", "glyf", "loca", "cvt ", "fpgm", "prep", "gasp");
    private static final double OBLIQUE = -0.25;

    private final Map<Integer, Weight> weights = new TreeMap<>();
    private final FontRenderContext context = new FontRenderContext(null, true, true);

    private record Weight(Font font, byte[] file, double ascent, double descent) {
    }

    public InterTypesetter() {
        FILES.forEach((weight, name) -> weights.put(weight, load("ui/fonts/Inter-" + name + ".ttf")));
    }

    @Override
    public boolean hasFamily(String family) {
        return FAMILY.equalsIgnoreCase(family.strip());
    }

    @Override
    public Metrics metrics(Face face) {
        Weight weight = weight(face);
        return new Metrics(Math.round(weight.ascent() * face.size()), Math.round(weight.descent() * face.size()));
    }

    @Override
    public double advance(Face face, String text) {
        return text.isEmpty() ? 0 : layout(face, text).getAdvance();
    }

    @Override
    public String outline(Face face, String text, double x, double baseline) {
        if (text.isEmpty()) {
            return "";
        }
        Shape shape = layout(face, text).getOutline(AffineTransform.getTranslateInstance(x, baseline));
        StringBuilder d = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> d.append('M').append(n(c[0])).append(' ').append(n(c[1]));
                case PathIterator.SEG_LINETO -> d.append('L').append(n(c[0])).append(' ').append(n(c[1]));
                case PathIterator.SEG_QUADTO -> d.append('Q').append(n(c[0])).append(' ').append(n(c[1])).append(' ')
                        .append(n(c[2])).append(' ').append(n(c[3]));
                case PathIterator.SEG_CUBICTO -> d.append('C').append(n(c[0])).append(' ').append(n(c[1])).append(' ')
                        .append(n(c[2])).append(' ').append(n(c[3])).append(' ').append(n(c[4])).append(' ')
                        .append(n(c[5]));
                default -> d.append('Z');
            }
        }
        return d.toString();
    }

    @Override
    public byte[] subset(Face face, String characters) {
        try {
            TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(weight(face).file()));
            TTFSubsetter subsetter = new TTFSubsetter(font, SUBSET_TABLES);
            Set<Integer> codePoints = characters.codePoints().boxed().collect(Collectors.toSet());
            codePoints.add((int) ' ');
            subsetter.addAll(codePoints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            subsetter.writeToStream(out);
            font.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TextLayout layout(Face face, String text) {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.SIZE, (float) face.size());
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        attributes.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        if (face.tracking() != 0) {
            attributes.put(TextAttribute.TRACKING, (float) face.tracking());
        }
        Font font = weight(face).font().deriveFont(attributes);
        if (face.italic()) {
            font = font.deriveFont(AffineTransform.getShearInstance(OBLIQUE, 0));
        }
        return new TextLayout(text, font, context);
    }

    /** Ближайшая насыщенность из набора: 300 — к 400, 800 — к 700. */
    private Weight weight(Face face) {
        return weights.entrySet().stream()
                .min(Comparator.comparingInt(e -> Math.abs(e.getKey() - face.weight())))
                .orElseThrow().getValue();
    }

    private static Weight load(String path) {
        try (InputStream in = InterTypesetter.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException(path);
            }
            byte[] file = in.readAllBytes();
            Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file));
            try (TrueTypeFont parsed = new TTFParser().parse(new RandomAccessReadBuffer(file))) {
                double unitsPerEm = parsed.getHeader().getUnitsPerEm();
                return new Weight(font, file, parsed.getHorizontalHeader().getAscender() / unitsPerEm,
                        Math.abs(parsed.getHorizontalHeader().getDescender()) / unitsPerEm);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (FontFormatException e) {
            throw new IllegalStateException(path, e);
        }
    }

    private static String n(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }
}
