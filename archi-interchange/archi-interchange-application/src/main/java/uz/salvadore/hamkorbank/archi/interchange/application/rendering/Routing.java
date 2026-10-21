package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Маршрут связи — тот же, что рисует канва: через точки перегиба, а без них
 * ортогонально. Расхождение здесь видно сразу — линия на экране и в файле шла бы
 * разными путями, поэтому алгоритм повторяет канву шаг в шаг.
 */
public final class Routing {

    /** Петля связи узла с самим собой выступает вправо на столько. */
    private static final double LOOP = 24;

    /** Предел цепочки «связь к связи», как у канвы: дальше конец неизвестен, цикл не зацикливает. */
    private static final int LINK_DEPTH = 8;

    private Routing() {
    }

    /** Точка на холсте. */
    public record Point(double x, double y) {
    }

    /** Прямоугольник фигуры в абсолютных координатах. */
    public record Rect(double x, double y, double width, double height) {

        /** Центр прямоугольника. */
        public Point center() {
            return new Point(x + width / 2, y + height / 2);
        }
    }

    /** Точка перегиба Archi: смещения от центров источника и цели. */
    public record Bendpoint(double startX, double startY, double endX, double endY) {
    }

    /** Связь как конец другой связи: её концы и точки перегиба. */
    public record Link(String sourceId, String targetId, List<Bendpoint> bendpoints) {

        public Link {
            bendpoints = List.copyOf(bendpoints);
        }
    }

    /**
     * Прямоугольник конца связи: фигура, а у связи к связи — квадрат в пиксель посередине
     * маршрута связи-конца, как крепит канва. Конец не найден, цепочка длиннее предела
     * или замкнута в цикл — пусто.
     *
     * @param shapes прямоугольники фигур по идентификатору узла
     * @param links  связи по идентификатору ребра
     */
    public static Optional<Rect> endpoint(String id, Map<String, Rect> shapes, Map<String, Link> links) {
        return endpoint(id, shapes, links, 0);
    }

    private static Optional<Rect> endpoint(String id, Map<String, Rect> shapes, Map<String, Link> links, int depth) {
        Rect shape = shapes.get(id);
        if (shape != null) {
            return Optional.of(shape);
        }
        Link link = links.get(id);
        if (link == null || depth > LINK_DEPTH) {
            return Optional.empty();
        }
        Optional<Rect> source = endpoint(link.sourceId(), shapes, links, depth + 1);
        Optional<Rect> target = endpoint(link.targetId(), shapes, links, depth + 1);
        if (source.isEmpty() || target.isEmpty()) {
            return Optional.empty();
        }
        Point p = midpoint(route(source.get(), target.get(), link.bendpoints()));
        return Optional.of(new Rect(p.x() - 0.5, p.y() - 0.5, 1, 1));
    }

    /**
     * Абсолютные точки перегиба. Каждая хранится двумя смещениями — от центра
     * источника и от центра цели — и смешивается с весом (i + 1) / (n + 1).
     */
    public static List<Point> absoluteBendpoints(Rect source, Rect target, List<Bendpoint> bendpoints) {
        Point sc = source.center();
        Point tc = target.center();
        int n = bendpoints.size();
        List<Point> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Bendpoint b = bendpoints.get(i);
            double w = (double) (i + 1) / (n + 1);
            points.add(new Point((sc.x() + b.startX()) * (1 - w) + (tc.x() + b.endX()) * w,
                    (sc.y() + b.startY()) * (1 - w) + (tc.y() + b.endY()) * w));
        }
        return points;
    }

    /** Точка на границе прямоугольника по лучу из его центра — якорь «chopbox», как у Archi. */
    static Point chopbox(Rect rect, Point toward) {
        Point c = rect.center();
        double dx = toward.x() - c.x();
        double dy = toward.y() - c.y();
        if (dx == 0 && dy == 0) {
            return c;
        }
        double sx = dx != 0 ? rect.width() / 2 / Math.abs(dx) : Double.POSITIVE_INFINITY;
        double sy = dy != 0 ? rect.height() / 2 / Math.abs(dy) : Double.POSITIVE_INFINITY;
        double s = Math.min(sx, sy);
        return new Point(c.x() + dx * s, c.y() + dy * s);
    }

    /**
     * Маршрут: с точками перегиба — через них; без них — прямой отрезок, если концы
     * перекрываются по одной оси, иначе ступенька с изломом посередине.
     */
    static List<Point> route(Rect source, Rect target, List<Bendpoint> bendpoints) {
        if (!bendpoints.isEmpty()) {
            List<Point> middle = absoluteBendpoints(source, target, bendpoints);
            List<Point> points = new ArrayList<>();
            points.add(chopbox(source, middle.getFirst()));
            points.addAll(middle);
            points.add(chopbox(target, middle.getLast()));
            return points;
        }
        if (source.equals(target)) {
            return selfLoop(source);
        }
        double left = Math.max(source.x(), target.x());
        double right = Math.min(source.x() + source.width(), target.x() + target.width());
        double top = Math.max(source.y(), target.y());
        double bottom = Math.min(source.y() + source.height(), target.y() + target.height());
        Point sc = source.center();
        Point tc = target.center();
        boolean overlapX = right - left > 0;
        boolean overlapY = bottom - top > 0;

        if (overlapX && !overlapY) {
            double x = (left + right) / 2;
            boolean down = tc.y() > sc.y();
            return List.of(new Point(x, down ? source.y() + source.height() : source.y()),
                    new Point(x, down ? target.y() : target.y() + target.height()));
        }
        if (overlapY && !overlapX) {
            double y = (top + bottom) / 2;
            boolean rightward = tc.x() > sc.x();
            return List.of(new Point(rightward ? source.x() + source.width() : source.x(), y),
                    new Point(rightward ? target.x() : target.x() + target.width(), y));
        }
        if (overlapX) {
            // Вложенные или наложенные фигуры: ортогонального маршрута нет.
            return List.of(chopbox(source, tc), chopbox(target, sc));
        }
        boolean rightward = tc.x() > sc.x();
        Point start = new Point(rightward ? source.x() + source.width() : source.x(), sc.y());
        Point end = new Point(rightward ? target.x() : target.x() + target.width(), tc.y());
        double midX = (start.x() + end.x()) / 2;
        return List.of(start, new Point(midX, start.y()), new Point(midX, end.y()), end);
    }

    private static List<Point> selfLoop(Rect rect) {
        double x = rect.x() + rect.width();
        double y = rect.y() + rect.height() / 3;
        return List.of(new Point(x, y), new Point(x + LOOP, y), new Point(x + LOOP, y + rect.height() / 3),
                new Point(x, y + rect.height() / 3));
    }

    /** Путь SVG через точки со скруглёнными изломами. */
    static String roundedPath(List<Point> points, double radius) {
        if (points.isEmpty()) {
            return "";
        }
        StringBuilder d = new StringBuilder("M").append(Svg.num(points.getFirst().x())).append(' ')
                .append(Svg.num(points.getFirst().y()));
        for (int i = 1; i < points.size() - 1; i++) {
            Point prev = points.get(i - 1);
            Point at = points.get(i);
            Point next = points.get(i + 1);
            double inLen = Math.hypot(at.x() - prev.x(), at.y() - prev.y());
            double outLen = Math.hypot(next.x() - at.x(), next.y() - at.y());
            double r = Math.min(radius, Math.min(inLen / 2, outLen / 2));
            if (r <= 0) {
                d.append(" L").append(Svg.num(at.x())).append(' ').append(Svg.num(at.y()));
                continue;
            }
            double ax = at.x() - (at.x() - prev.x()) / inLen * r;
            double ay = at.y() - (at.y() - prev.y()) / inLen * r;
            double bx = at.x() + (next.x() - at.x()) / outLen * r;
            double by = at.y() + (next.y() - at.y()) / outLen * r;
            d.append(" L").append(Svg.num(ax)).append(' ').append(Svg.num(ay))
                    .append(" Q").append(Svg.num(at.x())).append(' ').append(Svg.num(at.y())).append(' ')
                    .append(Svg.num(bx)).append(' ').append(Svg.num(by));
        }
        Point last = points.getLast();
        return d.append(" L").append(Svg.num(last.x())).append(' ').append(Svg.num(last.y())).toString();
    }

    /** Середина ломаной по длине — место подписи и точка, куда цепляются связи со связью. */
    static Point midpoint(List<Point> points) {
        if (points.size() == 1) {
            return points.getFirst();
        }
        double[] lengths = new double[points.size() - 1];
        double total = 0;
        for (int i = 0; i < lengths.length; i++) {
            lengths[i] = Math.hypot(points.get(i + 1).x() - points.get(i).x(),
                    points.get(i + 1).y() - points.get(i).y());
            total += lengths[i];
        }
        double rest = total / 2;
        for (int i = 0; i < lengths.length; i++) {
            if (rest <= lengths[i] && lengths[i] > 0) {
                double t = rest / lengths[i];
                Point a = points.get(i);
                Point b = points.get(i + 1);
                return new Point(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
            }
            rest -= lengths[i];
        }
        return points.getLast();
    }
}
