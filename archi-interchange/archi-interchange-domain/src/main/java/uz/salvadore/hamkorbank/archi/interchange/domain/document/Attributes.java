package uz.salvadore.hamkorbank.archi.interchange.domain.document;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Атрибуты узла в порядке файла. Все — и те, что метамодель знает, и те, что нет:
 * незнакомый атрибут хранится наравне со знакомым и выводится на своём месте
 * (INV-IXC-001). Порядок — часть детерминированного вывода (INV-IXC-004): он
 * берётся из файла, а не из обхода хеш-таблицы.
 */
public record Attributes(List<Attribute> list) implements Iterable<Attribute> {

    public Attributes {
        list = List.copyOf(list);
        Set<String> names = new HashSet<>();
        for (Attribute attribute : list) {
            if (!names.add(attribute.name())) {
                throw new IllegalArgumentException("атрибут повторяется: " + attribute.name());
            }
        }
    }

    /** {@code of("name", "Каналы", "id", "id-…")} — пары имя–значение по порядку. */
    public static Attributes of(String... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("ожидались пары имя–значение");
        }
        List<Attribute> list = new ArrayList<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            list.add(new Attribute(namesAndValues[i], namesAndValues[i + 1]));
        }
        return new Attributes(list);
    }

    public Optional<String> get(String name) {
        return list.stream().filter(a -> a.name().equals(name)).map(Attribute::value).findFirst();
    }

    @Override
    public Iterator<Attribute> iterator() {
        return list.iterator();
    }
}
