package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

/**
 * Экранирование как у EMF, которым пишет Archi: одинаковые правила у читателя
 * (для непрозрачных фрагментов) и у писателя, иначе фрагмент и окружающий его
 * текст разошлись бы в записи одного и того же символа.
 *
 * <p>Переводы строк и табуляция в атрибутах — ссылками на символ: парсер XML
 * обязан нормализовать их буквальные копии в пробел, и значение не пережило бы
 * второго чтения. Возврат каретки в тексте — по той же причине.
 */
final class XmlEscaping {

    private XmlEscaping() {
    }

    static String attribute(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\n' -> out.append("&#xA;");
                case '\r' -> out.append("&#xD;");
                case '\t' -> out.append("&#x9;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String text(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '\r' -> out.append("&#xD;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
