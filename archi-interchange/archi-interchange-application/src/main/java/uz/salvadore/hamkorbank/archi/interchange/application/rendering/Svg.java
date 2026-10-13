package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Мелочи печати SVG: числа без лишних знаков, экранирование, цвета SVG 1.1. */
final class Svg {

    private static final Pattern RGBA = Pattern.compile(
            "rgba?\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*(?:,\\s*([\\d.]+)\\s*)?\\)");

    private Svg() {
    }

    /**
     * Число с точностью до сотой без хвостовых нулей: {@code 12}, {@code 0.5}, {@code -3.25}.
     * Одинаковое значение печатается одинаково — файл детерминирован.
     */
    static String num(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.signum() == 0) {
            return "0";
        }
        return rounded.toPlainString();
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        continue;
                    }
                    out.append(c);
                }
            }
        }
        return out.toString();
    }

    /**
     * Краска в атрибутах SVG 1.1: {@code rgba()} — цвет и отдельная непрозрачность.
     * Batik, Inkscape и Word {@code rgba()} в {@code fill} не понимают.
     *
     * @param attribute {@code fill} или {@code stroke}
     */
    static String paint(String attribute, String color) {
        Matcher m = RGBA.matcher(color.strip());
        if (!m.matches()) {
            return attribute + "=\"" + escape(color) + "\"";
        }
        String hex = String.format(Locale.ROOT, "#%02X%02X%02X", channel(m.group(1)), channel(m.group(2)),
                channel(m.group(3)));
        String result = attribute + "=\"" + hex + "\"";
        if (m.group(4) != null && Double.parseDouble(m.group(4)) < 1) {
            result += " " + attribute + "-opacity=\"" + num(Double.parseDouble(m.group(4))) + "\"";
        }
        return result;
    }

    private static int channel(String value) {
        return (int) Math.max(0, Math.min(255, Math.round(Double.parseDouble(value))));
    }
}
