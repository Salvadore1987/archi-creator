package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Box;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.ShapeEntry;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Size;

/**
 * Контуры семи примитивов {@code shapes.json} — те же пути, что рисует канва.
 * Контур отступает внутрь на половину толщины обводки, иначе край обрезается.
 */
final class Outlines {

    /** Высота ярлыка группы и предел его ширины — из описания примитива {@code tab}. */
    private static final double TAB_HEIGHT = 18;
    private static final double TAB_MAX_WIDTH = 120;
    private static final double DOG_EAR = 12;
    private static final double BEVEL = 8;
    private static final double INSET = 0.5;

    private Outlines() {
    }

    /** Путь контура в координатах фигуры размера {@code size}. */
    static String path(ShapeEntry entry, Size size, double radius) {
        double x0 = INSET;
        double y0 = INSET;
        double x1 = size.width() - INSET;
        double y1 = size.height() - INSET;
        double w = x1 - x0;
        double h = y1 - y0;
        return switch (entry.outline()) {
            case "stadium" -> roundedRect(x0, y0, w, h, h / 2);
            case "circle" -> {
                double r = Math.min(w, h) / 2;
                double cx = x0 + w / 2;
                double cy = y0 + h / 2;
                yield "M" + n(cx - r) + " " + n(cy) + " A" + n(r) + " " + n(r) + " 0 1 0 " + n(cx + r) + " " + n(cy)
                        + " A" + n(r) + " " + n(r) + " 0 1 0 " + n(cx - r) + " " + n(cy) + " Z";
            }
            case "tab" -> {
                double tab = Math.min(TAB_MAX_WIDTH, w / 3);
                yield "M" + n(x0) + " " + n(y0) + " H" + n(x0 + tab) + " V" + n(y0 + TAB_HEIGHT) + " H" + n(x1)
                        + " V" + n(y1) + " H" + n(x0) + " Z M" + n(x0) + " " + n(y0 + TAB_HEIGHT) + " H" + n(x0 + tab);
            }
            case "dog-ear" -> "M" + n(x0) + " " + n(y0) + " H" + n(x1 - DOG_EAR) + " L" + n(x1) + " " + n(y0 + DOG_EAR)
                    + " V" + n(y1) + " H" + n(x0) + " Z M" + n(x1 - DOG_EAR) + " " + n(y0) + " V" + n(y0 + DOG_EAR)
                    + " H" + n(x1);
            case "bevel" -> {
                double c = Math.min(BEVEL, Math.min(w / 2, h / 2));
                yield "M" + n(x0 + c) + " " + n(y0) + " H" + n(x1 - c) + " L" + n(x1) + " " + n(y0 + c) + " V" + n(y1 - c)
                        + " L" + n(x1 - c) + " " + n(y1) + " H" + n(x0 + c) + " L" + n(x0) + " " + n(y1 - c)
                        + " V" + n(y0 + c) + " Z";
            }
            default -> roundedRect(x0, y0, w, h, radius);
        };
    }

    /** Отрицательное смещение — от дальнего края, неположительный размер — размер фигуры минус модуль. */
    static Box resolve(Box box, Size size) {
        double x = box.x() < 0 ? size.width() + box.x() : box.x();
        double y = box.y() < 0 ? size.height() + box.y() : box.y();
        double width = box.width() <= 0 ? size.width() + box.width() - x : box.width();
        double height = box.height() <= 0 ? size.height() + box.height() - y : box.height();
        return new Box(x, y, Math.max(0, width), Math.max(0, height));
    }

    private static String roundedRect(double x, double y, double w, double h, double radius) {
        double r = Math.max(0, Math.min(radius, Math.min(w / 2, h / 2)));
        if (r == 0) {
            return "M" + n(x) + " " + n(y) + " H" + n(x + w) + " V" + n(y + h) + " H" + n(x) + " Z";
        }
        return "M" + n(x + r) + " " + n(y) + " H" + n(x + w - r) + " A" + n(r) + " " + n(r) + " 0 0 1 " + n(x + w) + " "
                + n(y + r) + " V" + n(y + h - r) + " A" + n(r) + " " + n(r) + " 0 0 1 " + n(x + w - r) + " " + n(y + h)
                + " H" + n(x + r) + " A" + n(r) + " " + n(r) + " 0 0 1 " + n(x) + " " + n(y + h - r)
                + " V" + n(y + r) + " A" + n(r) + " " + n(r) + " 0 0 1 " + n(x + r) + " " + n(y) + " Z";
    }

    private static String n(double value) {
        return Svg.num(value);
    }
}
