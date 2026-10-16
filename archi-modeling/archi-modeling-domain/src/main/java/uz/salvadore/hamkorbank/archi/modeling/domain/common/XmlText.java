package uz.salvadore.hamkorbank.archi.modeling.domain.common;

import java.util.List;

/**
 * Текст, который модель может записать в файл Archi: только символы XML 1.0 — табуляция,
 * переводы строки и печатные символы, суррогатные пары — только парами. Управляющий символ
 * или одиночный суррогат сделали бы выгрузку нечитаемой или незаметно изменили бы данные.
 *
 * <p>Проверяется ввод пользователя в бизнес-методах агрегатов, а не конструкторы значений:
 * через конструкторы модель и загружается из хранилища, и старая запись не должна делать
 * модель нечитаемой.
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

    /** Строка без символов вне XML 1.0; {@code what} называет поле в отказе. */
    public static String require(String value, Message what) {
        if (!valid(value)) {
            throw ModelingDomainException.invalid(Message.of(ModelingMessages.TEXT_NOT_XML, what));
        }
        return value;
    }

    /** Свойства — ключи и значения без символов вне XML 1.0; {@code null} — допустимо. */
    public static List<PropertyEntry> require(List<PropertyEntry> properties) {
        if (properties != null) {
            for (PropertyEntry property : properties) {
                require(property.key(), Message.of(ModelingMessages.FIELD_PROPERTY_KEY));
                require(property.value(), Message.of(ModelingMessages.FIELD_PROPERTY_VALUE));
            }
        }
        return properties;
    }

    private static boolean allowed(char c) {
        return c == '\t' || c == '\n' || c == '\r' || c >= 0x20 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD;
    }
}
