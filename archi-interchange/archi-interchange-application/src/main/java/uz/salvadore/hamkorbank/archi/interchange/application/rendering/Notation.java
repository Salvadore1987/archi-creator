package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Визуальный язык, общий с канвой: силуэты фигур, токены стиля и символы иконок.
 * Собирается из тех же файлов, что читает фронтенд, — второй копии значений нет,
 * иначе экспорт разошёлся бы с экраном.
 *
 * @param shapes  силуэт по {@code xsi:type}; тип без записи рисуется {@code fallback}
 * @param layers  заливка и обводка по ключу слоя токенов ({@code application}, …)
 * @param values  прочие токены по ключу: {@code node_radius} → {@code 8px}, {@code paper} → цвет
 * @param icons   символы спрайта по {@code id}: тело символа в квадрате 16×16
 * @param family  семейство шрифта подписей, которым сервер располагает
 */
public record Notation(Size defaultSize, Map<String, Size> defaultSizeByType, Map<String, ShapeEntry> shapes,
                       ShapeEntry fallback, Map<String, LayerPaint> layers, Map<String, String> values,
                       Map<String, String> icons, String family) {

    public Notation {
        Objects.requireNonNull(defaultSize, "defaultSize");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(family, "family");
        defaultSizeByType = Map.copyOf(defaultSizeByType);
        shapes = Map.copyOf(shapes);
        layers = Map.copyOf(layers);
        values = Map.copyOf(values);
        icons = Map.copyOf(icons);
    }

    /** Силуэт по типу; тип без записи — заглушка с настоящим именем. */
    public ShapeEntry shapeOf(String archiType) {
        return shapes.getOrDefault(archiType, fallback);
    }

    /** Есть ли у типа свой силуэт, а не заглушка. */
    public boolean hasOwnShape(String archiType) {
        return shapes.containsKey(archiType);
    }

    /** Размер {@code -1} в файле Archi означает «по умолчанию для типа». */
    public Size effectiveSize(String archiType, double width, double height) {
        Size fallbackSize = defaultSizeByType.getOrDefault(archiType, defaultSize);
        return new Size(width > 0 ? width : fallbackSize.width(), height > 0 ? height : fallbackSize.height());
    }

    /** Значение токена как есть; токена нет — ошибка файла токенов, а не повод подставить своё. */
    public String value(String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalStateException(key);
        }
        return value;
    }

    /** Числовой токен в пикселях: {@code 8px} → 8. */
    public double pixels(String key) {
        return Double.parseDouble(value(key).replace("px", "").strip());
    }

    /** Заливка и обводка слоя; неизвестный ключ — слой {@code other}. */
    public LayerPaint layer(String key) {
        return Optional.ofNullable(layers.get(key)).orElseGet(() -> layers.get("other"));
    }

    /** Размер фигуры. */
    public record Size(double width, double height) {
    }

    /** Прямоугольник в координатах фигуры; отрицательное — от дальнего края. */
    public record Box(double x, double y, double width, double height) {
    }

    /** Угловая иконка фигуры: символ спрайта, место и размер. */
    public record CornerIcon(String spriteId, double x, double y, double size) {
    }

    /** Цвета заливки и обводки слоя. */
    public record LayerPaint(String fill, String border) {
    }

    /**
     * Запись силуэта: имя примитива контура, угловая иконка, область подписи,
     * токен радиуса, ключ слоя ({@code null} — слой элемента) и пунктир обводки.
     * Иконки, области подписи и пунктира нет — {@code null}.
     */
    public record ShapeEntry(String outline, CornerIcon icon, Box labelBox, String radiusToken, String layer,
                             String dash) {

        public ShapeEntry {
            Objects.requireNonNull(outline, "outline");
            Objects.requireNonNull(radiusToken, "radiusToken");
        }
    }
}
