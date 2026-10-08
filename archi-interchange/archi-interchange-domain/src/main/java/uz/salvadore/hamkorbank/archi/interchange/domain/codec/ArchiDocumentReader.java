package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.io.InputStream;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/** Порт чтения {@code .archimate} (FR-01). */
public interface ArchiDocumentReader {

    /**
     * Строит документ в порядке файла: идентификаторы буквально, незнакомое —
     * непрозрачными фрагментами по адресу (INV-IXC-001).
     *
     * @throws CorruptDocumentException если данные повреждены (FR-50) — со всеми
     *         найденными дефектами и местом каждого. Повреждённый файл не
     *         принимается ни в каком режиме (INV-IXC-007)
     */
    ModelDocument read(InputStream in);
}
