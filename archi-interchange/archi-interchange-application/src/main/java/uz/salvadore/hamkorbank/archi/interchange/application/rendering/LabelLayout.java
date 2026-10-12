package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter.Face;

/**
 * Перенос подписи по ширине — то, что браузер делает сам, а сервер обязан посчитать.
 *
 * <p>Правила те же, что у подписи на канве: строка рвётся в точках разрыва
 * (пробел, после дефиса, после косой черты), жадно; слово шире строки рвётся
 * по символам; хвостовые пробелы строки ширины не занимают. Сверх предела строк
 * последняя видимая строка заканчивается многоточием. Словарных переносов нет —
 * их нет и на канве: словарь зависит от платформы браузера.
 */
final class LabelLayout {

    static final String ELLIPSIS = "…";
    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");

    private final Typesetter typesetter;

    LabelLayout(Typesetter typesetter) {
        this.typesetter = typesetter;
    }

    /** Строки подписи и признак, что текст не поместился целиком. */
    record Lines(List<String> lines, boolean truncated) {
    }

    /**
     * @param maxLines   предел строк; {@code 0} — без предела
     * @param preserve   пробелы и переводы строк значимы (заметка); иначе пробелы схлопываются
     */
    Lines wrap(String text, Face face, double width, int maxLines, boolean preserve) {
        List<String> lines = new ArrayList<>();
        if (preserve) {
            for (String paragraph : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                lines.addAll(paragraph.isEmpty() ? List.of("") : wrapParagraph(paragraph, face, width));
            }
        } else {
            String collapsed = text.replaceAll("\\s+", " ").strip();
            if (!collapsed.isEmpty()) {
                lines.addAll(wrapParagraph(collapsed, face, width));
            }
        }
        if (maxLines <= 0 || lines.size() <= maxLines) {
            return new Lines(lines, false);
        }
        List<String> visible = new ArrayList<>(lines.subList(0, maxLines));
        visible.set(maxLines - 1, ellipsize(visible.get(maxLines - 1), face, width, true));
        return new Lines(visible, true);
    }

    /** Одна строка без переноса; не помещается — обрезается многоточием. */
    Lines single(String text, Face face, double width) {
        String line = text.replaceAll("\\s+", " ").strip();
        if (fits(line, face, width)) {
            return new Lines(List.of(line), false);
        }
        return new Lines(List.of(ellipsize(line, face, width, false)), true);
    }

    double width(String line, Face face) {
        return line.isEmpty() ? 0 : typesetter.advance(face, line);
    }

    private List<String> wrapParagraph(String paragraph, Face face, double width) {
        List<String> lines = new ArrayList<>();
        BreakIterator breaks = BreakIterator.getLineInstance(RUSSIAN);
        breaks.setText(paragraph);
        StringBuilder line = new StringBuilder();
        int start = breaks.first();
        for (int end = breaks.next(); end != BreakIterator.DONE; start = end, end = breaks.next()) {
            String segment = paragraph.substring(start, end);
            if (fits(line + segment, face, width)) {
                line.append(segment);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(stripTrailing(line.toString()));
                line.setLength(0);
            }
            // Слово шире строки рвётся по символам — только когда ему не хватает целой строки.
            String rest = segment;
            while (!fits(rest, face, width)) {
                int cut = longestFittingPrefix(rest, face, width);
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(stripTrailing(line.toString()));
        }
        return lines;
    }

    /** Хвостовые пробелы «висят» за краем строки и ширины не занимают. */
    private boolean fits(String text, Face face, double width) {
        return width(stripTrailing(text), face) <= width + 0.01;
    }

    private int longestFittingPrefix(String text, Face face, double width) {
        int cut = text.offsetByCodePoints(0, 1);
        while (cut < text.length()) {
            int next = text.offsetByCodePoints(cut, 1);
            if (!fits(text.substring(0, next), face, width)) {
                break;
            }
            cut = next;
        }
        return cut;
    }

    /**
     * Многоточие в конце строки: символы снимаются с конца, пока строка с ним не влезет.
     *
     * @param always ставить многоточие, даже если строка помещается (за ней есть невидимый текст)
     */
    private String ellipsize(String line, Face face, double width, boolean always) {
        String base = stripTrailing(line);
        if (always && fits(base + ELLIPSIS, face, width)) {
            return base + ELLIPSIS;
        }
        while (!base.isEmpty()) {
            base = stripTrailing(base.substring(0, base.offsetByCodePoints(base.length(), -1)));
            if (fits(base + ELLIPSIS, face, width)) {
                return base + ELLIPSIS;
            }
        }
        return ELLIPSIS;
    }

    private static String stripTrailing(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        return text.substring(0, end);
    }
}
