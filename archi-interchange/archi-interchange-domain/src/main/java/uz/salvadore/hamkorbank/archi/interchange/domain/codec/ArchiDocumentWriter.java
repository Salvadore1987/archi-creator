package uz.salvadore.hamkorbank.archi.interchange.domain.codec;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import uz.salvadore.hamkorbank.archi.interchange.domain.document.ModelDocument;

/** Порт записи {@code .archimate}. */
public interface ArchiDocumentWriter {

    /**
     * Пишет документ в порядке, в котором его построил читатель. Один и тот же
     * документ даёт побайтово один и тот же результат.
     */
    void write(ModelDocument document, OutputStream out);

    default byte[] write(ModelDocument document) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(document, out);
        return out.toByteArray();
    }
}
