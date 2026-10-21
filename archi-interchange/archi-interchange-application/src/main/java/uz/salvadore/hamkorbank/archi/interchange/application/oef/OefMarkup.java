package uz.salvadore.hamkorbank.archi.interchange.application.oef;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.codec.XmlEscaping;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentValue;

/** Разметка Open Exchange Format и разбор чисел файла — общее для частей писателя. */
final class OefMarkup {

    static final String XSI_TYPE = "xsi:type";
    private static final Pattern NCNAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.-]*$");

    private OefMarkup() {
    }

    /** Множество связей, оба конца которых выгружены — среди уже известных или среди них самих. */
    static Set<String> endpointsClosure(Map<String, DocumentNode> candidates, Set<String> known) {
        Set<String> kept = new LinkedHashSet<>(candidates.keySet());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (DocumentNode candidate : candidates.values()) {
                String id = candidate.archiId().value();
                if (!kept.contains(id)) {
                    continue;
                }
                String source = candidate.attribute("source").orElse("");
                String target = candidate.attribute("target").orElse("");
                boolean sourceKept = known.contains(source) || kept.contains(source);
                boolean targetKept = known.contains(target) || kept.contains(target);
                if (!sourceKept || !targetKept) {
                    kept.remove(id);
                    changed = true;
                }
            }
        }
        return kept;
    }

    /** Ссылка на объект: идентификатор как есть, если он NCName, иначе {@code id-} и 32 hex от него. */
    static String ref(String archiId) {
        if (NCNAME.matcher(archiId).matches()) {
            return archiId;
        }
        return "id-" + ContentHash.of(archiId.getBytes(StandardCharsets.UTF_8)).value().substring(0, 32);
    }

    static StringBuilder section(String tag, StringBuilder body) {
        if (body.isEmpty()) {
            return body;
        }
        return new StringBuilder("  <").append(tag).append(">\n").append(body).append("  </").append(tag).append(">\n");
    }

    static void langString(StringBuilder out, int depth, String tag, String text) {
        indent(out, depth).append('<').append(tag).append(" xml:lang=\"").append(OefWriter.LANGUAGE).append("\">")
                .append(XmlEscaping.text(text)).append("</").append(tag).append(">\n");
    }

    static void close(StringBuilder out, StringBuilder body, int depth, String tag) {
        if (body.isEmpty()) {
            out.append("/>\n");
        } else {
            out.append(">\n").append(body);
            indent(out, depth).append("</").append(tag).append(">\n");
        }
    }

    static StringBuilder indent(StringBuilder out, int depth) {
        return out.append("  ".repeat(depth));
    }

    static String attr(String value) {
        return XmlEscaping.attribute(value);
    }

    static long size(double value) {
        return Math.max(1, Math.round(value));
    }

    static String integer(double value) {
        return String.valueOf((long) value);
    }

    static double number(DocumentValue value, String name, double fallback) {
        return value.attribute(name).flatMap(OefMarkup::parseInt).map(Integer::doubleValue).orElse(fallback);
    }

    static Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Integer.parseInt(value.strip()));
        } catch (NumberFormatException notNumber) {
            return Optional.empty();
        }
    }

    static Set<String> union(Set<String> base, String... more) {
        Set<String> all = new HashSet<>(base);
        all.addAll(List.of(more));
        return Set.copyOf(all);
    }
}
