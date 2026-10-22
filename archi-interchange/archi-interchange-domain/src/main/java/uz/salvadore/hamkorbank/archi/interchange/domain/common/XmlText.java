package uz.salvadore.hamkorbank.archi.interchange.domain.common;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Текст, который можно записать в файл Archi: только символы XML 1.0 — табуляция,
 * переводы строки и печатные символы, суррогатные пары — только парами. Управляющий символ
 * или одиночный суррогат сделали бы файл нечитаемым или незаметно изменили бы данные.
 */
public final class XmlText {

    private static final String NAME_START = "[\\p{L}_]";
    private static final String NAME_PART = NAME_START + "[\\p{L}\\p{N}\\p{M}_.\\-\u00B7]*";
    private static final Pattern QUALIFIED_NAME = Pattern.compile(NAME_PART + "(?::" + NAME_PART + ")?");
    private static final Pattern ELEMENT_START = Pattern.compile("<" + NAME_START);

    private XmlText() {
    }

    /** Имя атрибута или элемента XML, с префиксом пространства имён или без: {@code xsi:type}, {@code name}. */
    public static boolean isName(@Nullable String value) {
        return value != null && QUALIFIED_NAME.matcher(value).matches();
    }

    /**
     * Разметка одного элемента: начинается открывающим тегом и кончается закрывающим.
     * Комментарий, инструкция обработки, секция CDATA и текст элементом не являются.
     */
    public static boolean isElement(@Nullable String xml) {
        return xml != null && ELEMENT_START.matcher(xml).lookingAt() && xml.endsWith(">");
    }

    /** Состоит ли строка только из символов XML 1.0; {@code null} — допустимо. */
    public static boolean valid(@Nullable String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    return false;
                }
                i++;
            } else if (Character.isLowSurrogate(c) || !allowed(c)) {
                return false;
            }
        }
        return true;
    }

    /** Строка без символов вне XML 1.0; {@code where} называет атрибут или элемент в отказе. */
    public static @Nullable String require(@Nullable String value, String where) {
        if (!valid(value)) {
            throw new InvalidValueException(InterchangeMessages.TEXT_NOT_XML, where);
        }
        return value;
    }

    private static boolean allowed(char c) {
        return c == '\t' || c == '\n' || c == '\r' || c >= 0x20 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD;
    }
}
