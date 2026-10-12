package uz.salvadore.hamkorbank.archi.interchange.application.port;

/**
 * SVG в PNG. Растр получается из того же SVG, что отдаётся векторным экспортом, —
 * своего пути отрисовки у него нет; реализация заменяема (Batik, {@code resvg}).
 */
public interface Rasterizer {

    /**
     * @param svg    документ SVG с подписями в кривых — растеризатору не нужны шрифты
     * @param width  ширина картинки в пикселях
     * @param height высота картинки в пикселях
     */
    byte[] png(byte[] svg, int width, int height);
}
