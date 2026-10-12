package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.io.InputStream;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/** Порт чтения {@code .archimate}. */
public interface ArchiDocumentReader {

    /**
     * Строит документ в порядке файла: идентификаторы буквально, незнакомое —
     * непрозрачными фрагментами по адресу.
     *
     * @throws CorruptDocumentException если данные повреждены — со всеми
     *         найденными дефектами и местом каждого. Повреждённый файл не
     *         принимается ни в каком режиме
     */
    ModelDocument read(InputStream in);
}
