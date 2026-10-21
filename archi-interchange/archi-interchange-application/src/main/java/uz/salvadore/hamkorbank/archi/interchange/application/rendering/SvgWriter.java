package uz.salvadore.hamkorbank.archi.interchange.application.rendering;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uz.salvadore.hamkorbank.archi.interchange.application.exporting.ExportLoss;
import uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter.Face;
import uz.salvadore.hamkorbank.archi.interchange.application.port.Typesetter.Metrics;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.EdgeNotation.Marker;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Box;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.CornerIcon;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.FontToken;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.ShapeEntry;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Notation.Size;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Link;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Point;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.Routing.Rect;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.ViewScene.Edge;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.ViewScene.Node;
import uz.salvadore.hamkorbank.archi.interchange.application.rendering.ViewScene.Style;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.DocumentNode;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;
import uz.salvadore.hamkorbank.archi.interchange.domain.exporting.LossObjectKind;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.Layer;

/**
 * Представление в SVG — так же, как его рисует канва: силуэты из {@code shapes.json},
 * краски из токенов, иконки из общего спрайта, связи по тому же маршруту
 * с наконечниками своего типа, подписи с переносом по ширине.
 *
 * <p>Печатается SVG 1.1 без CSS-свойств отрисовки: наконечники и ореол подписи —
 * явной геометрией, иконка — копией символа на месте, краска с прозрачностью —
 * цветом и {@code *-opacity}. Такой файл одинаково читают браузер, Batik, Inkscape
 * и Word. В текстовом режиме шрифт вшивается подмножеством глифов; в режиме
 * кривых подписи — контуры глифов, и шрифт получателю не нужен.
 *
 * <p>Порядок отрисовки — как у канвы: по уровням вложенности, на каждом уровне
 * сначала рёбра, затем узлы, внутри уровня — в порядке файла.
 */
public final class SvgWriter {

    private static final double HALO_WIDTH = 3;
    private static final double EDGE_LABEL_LIFT = 4;
    private static final double SUBTITLE_GAP = 1;
    private static final double ICON_GRID = 16;
    /** Сколько звеньев «связь к связи» пройти до узла, чтобы найти уровень отрисовки ребра. */
    private static final int LEVEL_DEPTH = 16;
    private static final int NAME_LINES = 2;
    /** Начертания сокращённой записи {@code font} CSS: {@code normal} и {@code bold}. */
    private static final int NORMAL_WEIGHT = 400;
    private static final int BOLD_WEIGHT = 700;
    private static final double PT = 4.0 / 3.0;
    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");
    private static final Pattern CUTOUT = Pattern.compile("var\\(--icon-cutout(?:,[^)]*)?\\)");
    private static final String FALLBACK_STACK = ", system-ui, sans-serif";

    private final Notation notation;
    private final Typesetter typesetter;
    private final TextCatalog texts;
    private final LabelLayout layout;

    public SvgWriter(Notation notation, Typesetter typesetter, TextCatalog texts) {
        this.notation = notation;
        this.typesetter = typesetter;
        this.texts = texts;
        this.layout = new LabelLayout(typesetter);
    }

    /**
     * @param outline    подписи кривыми: файл не зависит от шрифтов получателя
     * @param transparent без подложки цвета бумаги
     */
    public record Options(boolean outline, boolean transparent) {
    }

    /**
     * Готовый SVG и его рамка в координатах представления: {@code x}, {@code y} —
     * левый верхний угол с полями, {@code width} × {@code height} — размер картинки
     * при масштабе 100 %.
     */
    public record Rendered(byte[] svg, double x, double y, double width, double height, List<ExportLoss> losses) {

        public Rendered {
            svg = svg.clone();
            losses = List.copyOf(losses);
        }

        @Override
        public byte[] svg() {
            return svg.clone();
        }
    }

    /** Представление в SVG с отчётом о потерях и использованными глифами. */
    public Rendered write(ModelDocument document, DocumentNode view, Options options) {
        return new Drawing(new ViewScene(document, view, notation), options).draw();
    }

    /** Одна отрисовка: накапливает тело, рамку содержимого, потери и глифы для вшивания. */
    private final class Drawing {

        private final ViewScene scene;
        private final Options options;
        private final StringBuilder body = new StringBuilder();
        private final List<ExportLoss> losses = new ArrayList<>();
        private final Map<String, StringBuilder> glyphs = new TreeMap<>();
        private final Map<String, Face> faces = new TreeMap<>();
        private final Map<String, Rect> shapes = new HashMap<>();
        private final Map<String, Link> links = new HashMap<>();
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private int clips;

        Drawing(ViewScene scene, Options options) {
            this.scene = scene;
            this.options = options;
            scene.nodes().forEach(node -> shapes.put(node.archiId(), node.rect()));
            scene.edges().forEach(edge -> links.put(edge.archiId(),
                    new Link(edge.sourceId(), edge.targetId(), edge.bendpoints())));
        }

        Rendered draw() {
            List<Node> nodes = scene.nodes();
            List<Edge> edges = scene.edges();
            int maxDepth = nodes.stream().mapToInt(Node::depth).max().orElse(0);
            Map<Integer, List<Edge>> edgesByLevel = new TreeMap<>();
            for (Edge edge : edges) {
                Optional<Node> source = nodeOf(edge.sourceId(), 0);
                Optional<Node> target = nodeOf(edge.targetId(), 0);
                if (source.isEmpty() || target.isEmpty()) {
                    losses.add(new ExportLoss(edge.archiId(), LossObjectKind.EDGE, "route",
                            Message.of(InterchangeMessages.IMAGE_LOSS_EDGE)));
                    continue;
                }
                edgesByLevel.computeIfAbsent(Math.max(source.get().depth(), target.get().depth()),
                        k -> new ArrayList<>()).add(edge);
            }
            Comparator<Node> nodeOrder = Comparator.comparingInt(Node::position).thenComparingInt(Node::index);
            Comparator<Edge> edgeOrder = Comparator.comparingInt(Edge::position).thenComparingInt(Edge::index);
            for (int level = 0; level <= maxDepth; level++) {
                edgesByLevel.getOrDefault(level, List.of()).stream().sorted(edgeOrder).forEach(this::edge);
                int depth = level;
                nodes.stream().filter(n -> n.depth() == depth).sorted(nodeOrder).forEach(this::node);
            }
            return finish();
        }

        // ── Узлы ───────────────────────────────────────────────────

        private void node(Node node) {
            ShapeEntry entry = notation.shapeOf(node.shapeType());
            Rect rect = node.rect();
            Size size = new Size(rect.width(), rect.height());
            include(rect.x(), rect.y());
            include(rect.x() + rect.width(), rect.y() + rect.height());
            Paint paint = paint(entry, node);

            body.append("<g transform=\"translate(").append(Svg.num(rect.x())).append(' ')
                    .append(Svg.num(rect.y())).append(")\">");
            body.append("<path d=\"").append(Outlines.path(entry, size, notation.pixels(entry.radiusToken())))
                    .append("\" ").append(Svg.paint("fill", paint.fill())).append(' ')
                    .append(Svg.paint("stroke", node.opaque() ? notation.value("opaque_border") : paint.stroke()))
                    .append(" stroke-width=\"1\"");
            if (entry.dash() != null) {
                body.append(" stroke-dasharray=\"").append(entry.dash()).append('"');
            }
            body.append("/>");
            if (entry.icon() != null) {
                icon(entry.icon(), size, paint);
            }
            Optional.ofNullable(entry.labelBox()).map(box -> Outlines.resolve(box, size))
                    .filter(box -> box.width() > 0 && box.height() > 0)
                    .ifPresent(box -> label(node, entry, box, paint));
            body.append("</g>");

            if (node.opaque()) {
                losses.add(new ExportLoss(node.archiId(), LossObjectKind.NODE, "shape",
                        Message.of(InterchangeMessages.IMAGE_LOSS_PLACEHOLDER, shortType(node.shapeType()))));
            }
            styleLosses(node.archiId(), LossObjectKind.NODE, node.style());
            if (node.image()) {
                losses.add(new ExportLoss(node.archiId(), LossObjectKind.NODE, "image",
                        Message.of(InterchangeMessages.IMAGE_LOSS_IMAGE)));
            }
        }

        private void styleLosses(String archiId, LossObjectKind kind, Style style) {
            for (String attribute : style.rejected()) {
                losses.add(new ExportLoss(archiId, kind, "style",
                        Message.of(InterchangeMessages.IMAGE_LOSS_STYLE, attribute)));
            }
        }

        private void icon(CornerIcon icon, Size size, Paint paint) {
            String symbol = notation.icons().get(icon.spriteId());
            if (symbol == null) {
                return;
            }
            Box box = Outlines.resolve(new Box(icon.x(), icon.y(), icon.size(), icon.size()), size);
            String painted = CUTOUT.matcher(symbol.replace("currentColor", Svg.escape(paint.stroke())))
                    .replaceAll(Matcher.quoteReplacement(Svg.escape(paint.fill())));
            body.append("<g transform=\"translate(").append(Svg.num(box.x())).append(' ').append(Svg.num(box.y()))
                    .append(") scale(").append(Svg.num(box.width() / ICON_GRID)).append(")\">")
                    .append(painted).append("</g>");
        }

        private void label(Node node, ShapeEntry entry, Box box, Paint paint) {
            boolean tab = entry.outline().equals("tab");
            boolean note = entry.outline().equals("dog-ear");
            Typography type = tab ? tabTypography(node) : nameTypography(node, paint, note);
            LabelLayout.Lines lines = layout.wrap(type.text(), type.face(), box.width(), type.maxLines(), note);
            Optional<String> subtitle = subtitle(node);
            double content = lines.lines().size() * type.lineHeight()
                    + subtitle.map(s -> SUBTITLE_GAP + subtitleHeight()).orElse(0.0);
            double top = tab || note ? box.y() : box.y() + (box.height() - content) / 2;
            String align = align(node, tab);
            boolean overflowsBox = top < box.y() - 0.01 || top + content > box.y() + box.height() + 0.01;
            boolean clipped = overflowsBox || overflowsSide(lines, type.face(), box, align);
            if (lines.truncated() || overflowsBox && !type.text().isBlank()) {
                losses.add(new ExportLoss(node.archiId(), LossObjectKind.NODE, "label",
                        Message.of(InterchangeMessages.IMAGE_LOSS_LABEL)));
            }

            if (clipped) {
                openClip(box);
            }
            double lineTop = top;
            for (int i = 0; i < lines.lines().size(); i++) {
                double x = aligned(box, layout.width(lines.aligned(i), type.face()), align);
                text(lines.lines().get(i), type.face(), x, baseline(type.face(), lineTop, type.lineHeight()),
                        type.color(), note);
                lineTop += type.lineHeight();
            }
            if (subtitle.isPresent()) {
                subtitleLine(subtitle.get(), box, align, lineTop);
            }
            if (clipped) {
                body.append("</g>");
            }
        }

        /** Ярлык группы — всегда шрифтом подзаголовка, капителью: свой шрифт объекта его не меняет. */
        private Typography tabTypography(Node node) {
            FontToken font = notation.font("node_subtitle");
            Face face = new Face(notation.family(), font.size(), notation.weight("group_tab_font_weight"), false,
                    notation.ems("group_tab_letter_spacing"));
            return new Typography(face, layoutUnit(font.size() * font.lineHeight()),
                    notation.value("node_subtitle_color"), node.label().toUpperCase(RUSSIAN), 1);
        }

        /** Имя элемента или текст заметки: свой шрифт объекта сильнее токена; шрифта нет на сервере — потеря. */
        private Typography nameTypography(Node node, Paint paint, boolean note) {
            Optional<ArchiFont> own = Optional.ofNullable(node.style().font()).flatMap(ArchiFont::parse);
            String family = own.map(ArchiFont::family).orElse(notation.family());
            if (!typesetter.hasFamily(family)) {
                losses.add(new ExportLoss(node.archiId(), LossObjectKind.NODE, "font",
                        Message.of(InterchangeMessages.IMAGE_LOSS_FONT, family)));
                family = notation.family();
            }
            FontToken font = notation.font("node_font");
            double size = own.map(f -> f.sizePt() * PT).orElse(font.size());
            int weight = own.filter(ArchiFont::bold).isPresent() ? BOLD_WEIGHT
                    : notation.weight(note ? "note_font_weight" : "node_font_weight");
            Face face = new Face(family, size, weight, own.filter(ArchiFont::italic).isPresent(), 0);
            return new Typography(face, layoutUnit(size * font.lineHeight()), paint.text(), node.label(),
                    note ? 0 : NAME_LINES);
        }

        /** Подзаголовок заглушки — тип объекта; у поддержанного объекта его нет. */
        private Optional<String> subtitle(Node node) {
            return node.opaque()
                    ? Optional.of(texts.text(Message.of(InterchangeMessages.IMAGE_OPAQUE_SUBTITLE,
                    shortType(node.shapeType()))))
                    : Optional.empty();
        }

        private Face subtitleFace() {
            return new Face(notation.family(), notation.font("node_subtitle").size(), NORMAL_WEIGHT, false, 0);
        }

        private double subtitleHeight() {
            FontToken font = notation.font("node_subtitle");
            return layoutUnit(font.size() * font.lineHeight());
        }

        private void subtitleLine(String subtitle, Box box, String align, double lineTop) {
            Face face = subtitleFace();
            String single = layout.single(subtitle, face, box.width()).lines().getFirst();
            double x = aligned(box, layout.width(single, face), align);
            text(single, face, x, baseline(face, lineTop + SUBTITLE_GAP, subtitleHeight()),
                    notation.value("node_subtitle_color"), false);
        }

        /** Выравнивание Archi: 1 — влево, 4 — вправо, прочее — по центру; без своего — ярлык влево. */
        private String align(Node node, boolean tab) {
            return Optional.ofNullable(node.style().textAlignment())
                    .map(a -> a == 1 ? "left" : a == 4 ? "right" : "center")
                    .orElse(tab ? "left" : "center");
        }

        /** Выходит ли какая-нибудь строка за боковые края области подписи. */
        private boolean overflowsSide(LabelLayout.Lines lines, Face face, Box box, String align) {
            for (int i = 0; i < lines.lines().size(); i++) {
                double x = aligned(box, layout.width(lines.aligned(i), face), align);
                if (x < box.x() - 0.01 || x + layout.width(lines.lines().get(i), face) > box.x() + box.width() + 0.01) {
                    return true;
                }
            }
            return false;
        }

        private void openClip(Box box) {
            String id = "c" + (++clips);
            body.append("<clipPath id=\"").append(id).append("\"><rect x=\"").append(Svg.num(box.x()))
                    .append("\" y=\"").append(Svg.num(box.y())).append("\" width=\"").append(Svg.num(box.width()))
                    .append("\" height=\"").append(Svg.num(box.height())).append("\"/></clipPath>")
                    .append("<g clip-path=\"url(#").append(id).append(")\">");
        }

        private double aligned(Box box, double lineWidth, String align) {
            return switch (align) {
                case "left" -> box.x();
                case "right" -> box.x() + box.width() - lineWidth;
                default -> box.x() + (box.width() - lineWidth) / 2;
            };
        }

        /**
         * Базовая линия в межстрочном интервале — как ставит её браузер: половина зазора
         * между высотой строки и высотой шрифта сверху, округлённая вниз до пикселя.
         */
        private double baseline(Face face, double lineTop, double lineHeight) {
            Metrics metrics = typesetter.metrics(face);
            return lineTop + Math.floor((lineHeight - (metrics.ascent() + metrics.descent())) / 2) + metrics.ascent();
        }

        private Paint paint(ShapeEntry entry, Node node) {
            String layer = entry.layer() != null ? entry.layer() : layerToken(node.layer());
            String fill;
            String stroke;
            switch (layer) {
                case "group" -> {
                    fill = notation.value("group_fill");
                    stroke = notation.value("group_border");
                }
                case "note" -> {
                    fill = notation.value("note_fill");
                    stroke = notation.value("note_border");
                }
                case "junction" -> {
                    fill = notation.value("edge_color");
                    stroke = notation.value("edge_color");
                }
                default -> {
                    fill = notation.layer(layer).fill();
                    stroke = notation.layer(layer).border();
                }
            }
            Style style = node.style();
            return new Paint(Objects.requireNonNullElse(style.fillColor(), fill),
                    Objects.requireNonNullElse(style.lineColor(), stroke),
                    Objects.requireNonNullElse(style.fontColor(), notation.value("paper_text")));
        }

        // ── Рёбра ──────────────────────────────────────────────────

        private void edge(Edge edge) {
            Optional<Rect> source = endpoint(edge.sourceId());
            Optional<Rect> target = endpoint(edge.targetId());
            if (source.isEmpty() || target.isEmpty()) {
                losses.add(new ExportLoss(edge.archiId(), LossObjectKind.EDGE, "route",
                        Message.of(InterchangeMessages.IMAGE_LOSS_EDGE)));
                return;
            }
            styleLosses(edge.archiId(), LossObjectKind.EDGE, edge.style());
            List<Point> points = Routing.route(source.get(), target.get(), edge.bendpoints());
            EdgeNotation notationOfEdge = EdgeNotation.of(edge.archiType(), edge.accessType(), edge.directed());
            String stroke = Objects.requireNonNullElse(edge.style().lineColor(), notation.value("edge_color"));
            points.forEach(p -> include(p.x(), p.y()));

            body.append("<g><path d=\"").append(Routing.roundedPath(points, notation.pixels("edge_corner_radius")))
                    .append("\" fill=\"none\" ").append(Svg.paint("stroke", stroke)).append(" stroke-width=\"")
                    .append(Svg.num(notation.pixels("edge_width"))).append('"');
            if (notationOfEdge.dash() != null) {
                body.append(" stroke-dasharray=\"").append(notationOfEdge.dash()).append('"');
            }
            body.append("/>");
            if (notationOfEdge.start() != null) {
                marker(notationOfEdge.start(), points.get(0), points.get(1), true);
            }
            if (notationOfEdge.end() != null) {
                marker(notationOfEdge.end(), points.getLast(), points.get(points.size() - 2), false);
            }
            if (edge.label() != null) {
                edgeLabel(edge.label(), Routing.midpoint(points), edge.style());
            }
            body.append("</g>");
        }

        /**
         * Наконечник явной геометрией: фигура поворачивается по направлению отрезка
         * у конца и крепится точкой ({@code refX}, 5); у начала связи — развёрнута наружу.
         * Цвет — цвет связей из токенов, как у канвы, а не собственный цвет линии.
         */
        private void marker(Marker marker, Point at, Point from, boolean start) {
            double angle = start
                    ? Math.toDegrees(Math.atan2(from.y() - at.y(), from.x() - at.x())) + 180
                    : Math.toDegrees(Math.atan2(at.y() - from.y(), at.x() - from.x()));
            String edgeColor = notation.value("edge_color");
            String fill = switch (marker.fill) {
                case EDGE -> Svg.paint("fill", edgeColor);
                case PAPER -> Svg.paint("fill", notation.value("paper"));
                case NONE -> "fill=\"none\"";
            };
            include(at.x() - marker.refX, at.y() - marker.refX);
            include(at.x() + marker.refX, at.y() + marker.refX);
            body.append("<path transform=\"translate(").append(Svg.num(at.x())).append(' ').append(Svg.num(at.y()))
                    .append(") rotate(").append(Svg.num(angle)).append(") translate(").append(Svg.num(-marker.refX))
                    .append(' ').append(Svg.num(-Marker.REF_Y)).append(")\" d=\"").append(marker.path).append("\" ")
                    .append(fill).append(' ').append(Svg.paint("stroke", edgeColor)).append(" stroke-width=\"")
                    .append(Svg.num(Marker.STROKE_WIDTH))
                    .append("\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>");
        }

        private void edgeLabel(String label, Point at, Style style) {
            FontToken labelFont = notation.font("edge_label_font");
            Face face = new Face(notation.family(), labelFont.size(), NORMAL_WEIGHT, false, 0);
            String line = label.replaceAll("\\s+", " ").strip();
            double width = layout.width(line, face);
            double x = at.x() - width / 2;
            double baseline = at.y() - EDGE_LABEL_LIFT;
            include(x, baseline - labelFont.size());
            include(x + width, baseline + 3);
            String paper = notation.value("paper");
            if (options.outline()) {
                String d = typesetter.outline(face, line, x, baseline);
                body.append("<path d=\"").append(d).append("\" ").append(Svg.paint("fill", paper)).append(' ')
                        .append(Svg.paint("stroke", paper)).append(" stroke-width=\"").append(Svg.num(HALO_WIDTH))
                        .append("\" stroke-linejoin=\"round\"/>");
            } else {
                textElement(line, face, x, baseline, paper, false,
                        " " + Svg.paint("stroke", paper) + " stroke-width=\"" + Svg.num(HALO_WIDTH)
                                + "\" stroke-linejoin=\"round\"");
            }
            text(line, face, x, baseline,
                    Objects.requireNonNullElse(style.fontColor(), notation.value("edge_label_color")), false);
        }

        private Optional<Node> nodeOf(String endpoint, int guard) {
            Optional<Node> node = scene.node(endpoint);
            if (node.isPresent() || guard > LEVEL_DEPTH) {
                return node;
            }
            return scene.edge(endpoint).flatMap(e -> nodeOf(e.sourceId(), guard + 1));
        }

        /** Прямоугольник конца: узел, а у связи к связи — точка в середине маршрута ребра-конца. */
        private Optional<Rect> endpoint(String id) {
            return Routing.endpoint(id, shapes, links);
        }

        // ── Текст ──────────────────────────────────────────────────

        private void text(String line, Face face, double x, double baseline, String color, boolean preserve) {
            if (line.isEmpty()) {
                return;
            }
            if (options.outline()) {
                body.append("<path d=\"").append(typesetter.outline(face, line, x, baseline)).append("\" ")
                        .append(Svg.paint("fill", color)).append("/>");
                return;
            }
            textElement(line, face, x, baseline, color, preserve, "");
        }

        private void textElement(String line, Face face, double x, double baseline, String color, boolean preserve,
                                 String extra) {
            String key = face.weight() + (face.italic() ? "i" : "n");
            faces.putIfAbsent(key, face);
            glyphs.computeIfAbsent(key, k -> new StringBuilder()).append(line);
            body.append("<text x=\"").append(Svg.num(x)).append("\" y=\"").append(Svg.num(baseline))
                    .append("\" font-family=\"").append(Svg.escape(notation.family())).append(FALLBACK_STACK)
                    .append("\" font-size=\"").append(Svg.num(face.size())).append("\" font-weight=\"")
                    .append(face.weight()).append('"');
            if (face.italic()) {
                body.append(" font-style=\"italic\"");
            }
            if (face.tracking() != 0) {
                body.append(" letter-spacing=\"").append(Svg.num(face.tracking() * face.size())).append('"');
            }
            if (preserve) {
                body.append(" xml:space=\"preserve\"");
            }
            body.append(' ').append(Svg.paint("fill", color)).append(extra).append('>').append(Svg.escape(line))
                    .append("</text>");
        }

        // ── Сборка ─────────────────────────────────────────────────

        private void include(double x, double y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        private Rendered finish() {
            double margin = notation.pixels("grid_step");
            if (minX > maxX) {
                minX = 0;
                minY = 0;
                maxX = 0;
                maxY = 0;
            }
            double x = Math.floor(minX) - margin;
            double y = Math.floor(minY) - margin;
            double width = Math.ceil(maxX) - Math.floor(minX) + 2 * margin;
            double height = Math.ceil(maxY) - Math.floor(minY) + 2 * margin;

            StringBuilder svg = new StringBuilder(body.length() + 1024);
            svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    .append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" width=\"").append(Svg.num(width))
                    .append("\" height=\"").append(Svg.num(height)).append("\" viewBox=\"").append(Svg.num(x))
                    .append(' ').append(Svg.num(y)).append(' ').append(Svg.num(width)).append(' ')
                    .append(Svg.num(height)).append("\">\n");
            if (!glyphs.isEmpty()) {
                svg.append("<defs><style type=\"text/css\"><![CDATA[\n");
                glyphs.forEach((key, chars) -> {
                    Face face = faces.get(key);
                    byte[] font = typesetter.subset(face, chars.toString());
                    svg.append("@font-face{font-family:\"").append(notation.family()).append("\";font-weight:")
                            .append(face.weight()).append(";font-style:").append(face.italic() ? "italic" : "normal")
                            .append(";src:url(data:font/ttf;base64,").append(Base64.getEncoder().encodeToString(font))
                            .append(") format(\"truetype\");}\n");
                });
                svg.append("]]></style></defs>\n");
            }
            if (!options.transparent()) {
                svg.append("<rect x=\"").append(Svg.num(x)).append("\" y=\"").append(Svg.num(y)).append("\" width=\"")
                        .append(Svg.num(width)).append("\" height=\"").append(Svg.num(height)).append("\" ")
                        .append(Svg.paint("fill", notation.value("paper"))).append("/>\n");
            }
            svg.append(body).append("\n</svg>\n");
            return new Rendered(svg.toString().getBytes(StandardCharsets.UTF_8), x, y, width, height, losses);
        }
    }

    private record Paint(String fill, String stroke, String text) {
    }

    /** Оформление подписи: гарнитура, высота строки, цвет, текст к набору и предел строк ({@code 0} — без предела). */
    private record Typography(Face face, double lineHeight, String color, String text, int maxLines) {
    }

    /** Высота строки с точностью раскладки браузера — 1/64 пикселя, остаток отбрасывается. */
    private static double layoutUnit(double value) {
        return Math.floor(value * 64) / 64;
    }

    /** Ключ слоя модели — в ключ токена: физический слой красится как технологии, реализация — как прочее. */
    static String layerToken(Layer layer) {
        return switch (layer) {
            case BUSINESS -> "business";
            case APPLICATION -> "application";
            case TECHNOLOGY, PHYSICAL -> "technology";
            case MOTIVATION -> "motivation";
            case STRATEGY -> "strategy";
            default -> "other";
        };
    }

    private static String shortType(String archiType) {
        int colon = archiType.indexOf(':');
        return colon >= 0 ? archiType.substring(colon + 1) : archiType;
    }
}
