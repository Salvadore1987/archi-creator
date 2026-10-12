package uz.salvadore.hamkorbank.archi.interchange.application.port;

/**
 * Шрифт подписей экспорта: метрики, контуры глифов и подмножество файла.
 *
 * <p>Сервер располагает только своими файлами шрифта — системные не берутся,
 * иначе картинка зависела бы от образа, на котором собрана. Все величины —
 * в пикселях при масштабе 100 %, кернинг включён, как в браузере.
 */
public interface Typesetter {

    /** Есть ли у сервера файл этого семейства. Нет — подпись печатается шрифтом интерфейса. */
    boolean hasFamily(String family);

    /** Подъём и спуск начертания — по ним строка ставится в межстрочный интервал. */
    Metrics metrics(Face face);

    /** Ширина строки с кернингом и разрядкой. */
    double advance(Face face, String text);

    /** Контур строки путём SVG от начала базовой линии ({@code x}, {@code baseline}). */
    String outline(Face face, String text, double x, double baseline);

    /** Подмножество файла шрифта (TrueType) только с глифами этих символов — для вшивания в SVG. */
    byte[] subset(Face face, String characters);

    /**
     * Начертание.
     *
     * @param size     кегль в пикселях
     * @param weight   насыщенность CSS: 400, 500, 600, 700
     * @param tracking разрядка в долях кегля, как {@code letter-spacing} в em
     */
    record Face(String family, double size, int weight, boolean italic, double tracking) {
    }

    record Metrics(double ascent, double descent) {
    }
}
