package uz.salvadore.hamkorbank.archi.interchange.domain.common;

/**
 * Текст, который можно записать в файл Archi: только символы XML 1.0 — табуляция,
 * переводы строки и печатные символы, суррогатные пары — только парами. Управляющий символ
 * или одиночный суррогат сделали бы файл нечитаемым или незаметно изменили бы данные.
 */
public final class XmlText {

    private XmlText() {
    }

    /** Состоит ли строка только из символов XML 1.0; {@code null} — допустимо. */
    public static boolean valid(String value) {
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
    public static String require(String value, String where) {
        if (!valid(value)) {
            throw new InvalidValueException(InterchangeMessages.TEXT_NOT_XML, where);
        }
        return value;
    }

    private static boolean allowed(char c) {
        return c == '\t' || c == '\n' || c == '\r' || c >= 0x20 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD;
    }
}
