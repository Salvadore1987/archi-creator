package uz.salvadore.hamkorbank.archi.interchange.domain.exporting;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ArchiId;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ContentHash;

/**
 * Имя файла объекта модели в выгрузке: {@code <слаг>.<8 hex>}, например
 * {@code 01-kontekst-as-is.f11fbcb8}. Слаг — имя, транслитерированное в латиницу:
 * в списке файлов видно, что за объект, а кириллица в именах файлов ломается
 * на смешанных файловых системах и в архиваторах. Короткий идентификатор снимает
 * совпадение имён у разных объектов.
 */
public final class FileSlug {

    private static final int MAX_SLUG = 60;
    private static final int SHORT_ID = 8;
    private static final Pattern HEX_ID = Pattern.compile("^id-([0-9a-f]{8})");
    private static final Map<Character, String> CYRILLIC = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"), Map.entry('д', "d"),
            Map.entry('е', "e"), Map.entry('ё', "e"), Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
            Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"),
            Map.entry('о', "o"), Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
            Map.entry('ш', "sh"), Map.entry('щ', "shch"), Map.entry('ъ', ""), Map.entry('ы', "y"), Map.entry('ь', ""),
            Map.entry('э', "e"), Map.entry('ю', "yu"), Map.entry('я', "ya"), Map.entry('ў', "o"), Map.entry('қ', "q"),
            Map.entry('ғ', "g"), Map.entry('ҳ', "h"), Map.entry('і', "i"), Map.entry('ї', "yi"), Map.entry('є', "ye"));

    private FileSlug() {
    }

    /** {@code <слаг>.<8 hex>} без расширения. */
    public static String of(String name, ArchiId id) {
        return slug(name) + "." + shortId(id);
    }

    /**
     * Транслитерация в латиницу, нижний регистр, всё прочее — дефис, без дефисов
     * по краям и подряд; пустое имя — {@code unnamed}.
     */
    public static String slug(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        StringBuilder latin = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            String mapped = CYRILLIC.get(c);
            latin.append(mapped != null ? mapped : String.valueOf(c));
        }
        String ascii = Normalizer.normalize(latin, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_SLUG) {
            slug = slug.substring(0, MAX_SLUG).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "unnamed" : slug;
    }

    /** Первые 8 hex идентификатора Archi; идентификатор не в этой форме — 8 hex его отпечатка. */
    public static String shortId(ArchiId id) {
        Matcher hex = HEX_ID.matcher(id.value());
        if (hex.find()) {
            return hex.group(1);
        }
        return ContentHash.of(id.value().getBytes(java.nio.charset.StandardCharsets.UTF_8)).value()
                .substring(0, SHORT_ID);
    }
}
