package uz.salvadore.hamkorbank.archi.advisor.application.service;

import java.util.List;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextElement;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextRelation;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ContextView;
import uz.salvadore.hamkorbank.archi.advisor.domain.context.ModelContext;

/**
 * Компактный JSON для сообщения модели: короткие имена полей, без пробелов и без
 * пустых значений. Размер контекста — деньги, поэтому формат минимальный и
 * детерминированный: порядок полей и записей — порядок контекста.
 */
final class ContextJson {

    private final StringBuilder out = new StringBuilder();

    private ContextJson() {
    }

    static String model(ModelContext context) {
        ContextJson json = new ContextJson();
        json.out.append('{');
        json.key("elements").out.append('[');
        List<ContextElement> elements = context.elements();
        for (int i = 0; i < elements.size(); i++) {
            ContextElement e = elements.get(i);
            json.comma(i).out.append('{');
            json.key("id").string(e.archiId()).out.append(',');
            json.key("type").string(e.archiType()).out.append(',');
            json.key("name").string(e.name());
            e.documentation().ifPresent(d -> json.out.append(',').append("\"doc\":").append(quote(d)));
            json.out.append('}');
        }
        json.out.append("],");
        json.key("relationships").out.append('[');
        List<ContextRelation> relations = context.relationships();
        for (int i = 0; i < relations.size(); i++) {
            ContextRelation r = relations.get(i);
            json.comma(i).out.append('{');
            json.key("id").string(r.archiId()).out.append(',');
            json.key("type").string(r.archiType()).out.append(',');
            json.key("source").string(r.sourceArchiId()).out.append(',');
            json.key("target").string(r.targetArchiId()).out.append('}');
        }
        json.out.append("],");
        json.key("views").out.append('[');
        List<ContextView> views = context.views();
        for (int i = 0; i < views.size(); i++) {
            ContextView v = views.get(i);
            json.comma(i).out.append('{');
            json.key("id").string(v.archiId()).out.append(',');
            json.key("name").string(v.name()).out.append(',');
            json.key("elements").out.append('[');
            for (int j = 0; j < v.elementArchiIds().size(); j++) {
                json.comma(j).string(v.elementArchiIds().get(j));
            }
            json.out.append("]}");
        }
        json.out.append("]}");
        return json.out.toString();
    }

    /** Объект из строк, списков строк и вложенных объектов — для служебной части сообщения. */
    static String object(Map<String, ?> fields) {
        ContextJson json = new ContextJson();
        json.value(fields);
        return json.out.toString();
    }

    private void value(Object value) {
        switch (value) {
            case null -> out.append("null");
            case Raw raw -> out.append(raw.json());
            case String s -> string(s);
            case Boolean b -> out.append(b);
            case Number n -> out.append(n);
            case Map<?, ?> map -> {
                out.append('{');
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    comma(i++).key(String.valueOf(entry.getKey()));
                    value(entry.getValue());
                }
                out.append('}');
            }
            case List<?> list -> {
                out.append('[');
                for (int i = 0; i < list.size(); i++) {
                    comma(i).value(list.get(i));
                }
                out.append(']');
            }
            default -> string(value.toString());
        }
    }

    /** Уже готовый JSON — вставляется как есть. */
    record Raw(String json) {
    }

    private ContextJson key(String name) {
        out.append('"').append(name).append("\":");
        return this;
    }

    private ContextJson string(String value) {
        out.append(quote(value));
        return this;
    }

    private ContextJson comma(int index) {
        if (index > 0) {
            out.append(',');
        }
        return this;
    }

    static String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}
