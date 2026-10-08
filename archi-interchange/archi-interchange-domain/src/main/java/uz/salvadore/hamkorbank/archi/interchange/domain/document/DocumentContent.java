package uz.salvadore.hamkorbank.archi.interchange.domain.document;

/**
 * Одна единица содержимого узла в порядке файла: вложенный узел, значение
 * ({@code documentation}, {@code property}, {@code bounds}…) или непрозрачный фрагмент.
 *
 * <p>Общий список, а не три отдельных: порядок между ними — тоже часть файла,
 * и писатель обязан его воспроизвести.
 */
public sealed interface DocumentContent permits DocumentNode, DocumentValue, RawXmlFragment {

    DocumentOrder order();
}
