package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.attr;
import static uz.salvadore.hamkorbank.archi.interchange.application.oef.OefMarkup.indent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.ArchiFont;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.Attribute;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;

/** Оформление объекта представления в {@code style} OEF; что не разобрано — в отчёт. */
final class OefStyle {

    private static final Pattern COLOR = Pattern.compile("^#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})$");

    private final OefPass pass;

    OefStyle(OefPass pass) {
        this.pass = pass;
    }

    /** {@code style} из собственного оформления объекта; что не разобрано — в отчёт. */
    void style(StringBuilder out, DocumentNode node, String kind, int depth) {
        String id = node.archiId().value();
        StringBuilder inner = new StringBuilder();
        color(inner, node, kind, "lineColor", "lineAlpha", depth + 1);
        color(inner, node, kind, "fillColor", "alpha", depth + 1);
        font(inner, node, kind, depth + 1);
        Optional<String> lineWidth = node.attribute("lineWidth");
        Optional<Integer> width = lineWidth.flatMap(OefMarkup::parseInt).filter(w -> w > 0);
        if (lineWidth.isPresent() && width.isEmpty()) {
            pass.styleLoss(id, kind, "lineWidth", lineWidth.get());
        }
        if (inner.isEmpty() && width.isEmpty()) {
            return;
        }
        indent(out, depth).append("<style");
        width.ifPresent(w -> out.append(" lineWidth=\"").append(w).append('"'));
        if (inner.isEmpty()) {
            out.append("/>\n");
        } else {
            out.append(">\n").append(inner);
            indent(out, depth).append("</style>\n");
        }
    }

    private void color(StringBuilder out, DocumentNode node, String kind, String colorName, String alphaName,
                       int depth) {
        String id = node.archiId().value();
        Optional<String> value = node.attribute(colorName).filter(v -> !v.isEmpty());
        Optional<int[]> rgb = value.flatMap(OefStyle::rgb);
        if (value.isPresent() && rgb.isEmpty()) {
            pass.styleLoss(id, kind, colorName, value.get());
        }
        Optional<String> alpha = node.attribute(alphaName);
        Optional<Integer> percent = alpha.flatMap(OefMarkup::parseInt).filter(a -> a >= 0 && a <= 255)
                .map(a -> (int) Math.round(a * 100.0 / 255));
        if (alpha.isPresent() && percent.isEmpty()) {
            pass.styleLoss(id, kind, alphaName, alpha.get());
        } else if (alpha.isPresent() && rgb.isEmpty()) {
            // Прозрачность в OEF — атрибут цвета: без собственного цвета её некуда записать.
            pass.attributeLoss(id, kind, new Attribute(alphaName, alpha.get()));
        }
        rgb.ifPresent(c -> {
            indent(out, depth).append('<').append(colorName).append(rgbAttributes(c));
            percent.ifPresent(a -> out.append(" a=\"").append(a).append('"'));
            out.append("/>\n");
        });
    }

    /** Шрифт и его цвет; строку шрифта разбирает {@link ArchiFont}, как для картинки. */
    private void font(StringBuilder out, DocumentNode node, String kind, int depth) {
        String id = node.archiId().value();
        Optional<String> font = node.attribute("font").filter(v -> !v.isEmpty());
        Optional<String> fontColor = node.attribute("fontColor").filter(v -> !v.isEmpty());
        Optional<int[]> color = fontColor.flatMap(OefStyle::rgb);
        if (fontColor.isPresent() && color.isEmpty()) {
            pass.styleLoss(id, kind, "fontColor", fontColor.get());
        }
        StringBuilder attributes = new StringBuilder();
        Optional<ArchiFont> parsed = font.flatMap(ArchiFont::parse);
        if (font.isPresent()) {
            if (parsed.isEmpty()) {
                pass.styleLoss(id, kind, "font", font.get());
            } else {
                attributes.append(" name=\"").append(attr(parsed.get().family())).append('"');
                double half = Math.round(parsed.get().sizePt() * 2) / 2.0;
                if (half >= 1) {
                    attributes.append(" size=\"").append(half == Math.floor(half) ? String.valueOf((long) half)
                            : String.valueOf(half)).append('"');
                }
                attributes.append(" style=\"").append(fontStyle(parsed.get())).append('"');
            }
        }
        if (attributes.isEmpty() && color.isEmpty()) {
            return;
        }
        indent(out, depth).append("<font").append(attributes);
        if (color.isEmpty()) {
            out.append("/>\n");
            return;
        }
        out.append(">\n");
        indent(out, depth + 1).append("<color").append(rgbAttributes(color.get())).append("/>\n");
        indent(out, depth).append("</font>\n");
    }

    private static Optional<int[]> rgb(String value) {
        Matcher m = COLOR.matcher(value.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[] {Integer.parseInt(m.group(1), 16), Integer.parseInt(m.group(2), 16),
                Integer.parseInt(m.group(3), 16)});
    }

    private static String rgbAttributes(int[] c) {
        return " r=\"" + c[0] + "\" g=\"" + c[1] + "\" b=\"" + c[2] + "\"";
    }

    private static String fontStyle(ArchiFont font) {
        List<String> styles = new ArrayList<>();
        if (font.bold()) {
            styles.add("bold");
        }
        if (font.italic()) {
            styles.add("italic");
        }
        return styles.isEmpty() ? "plain" : String.join(" ", styles).toLowerCase(Locale.ROOT);
    }
}
