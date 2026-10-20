package uz.salvadore.hamkorbank.archi.modeling.application.port;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.ModelRows;

/**
 * Снимок версии — файл {@code .archimate}, собранный из базы при сохранении.
 * Реализует interchange: формат файла — его язык; modeling отдаёт строки модели,
 * а хранит результат, сжатый, и его отпечаток.
 */
public interface SnapshotWriter {

    /** Несжатый XML. Детерминирован: одни строки — одни байты. */
    byte[] write(ModelRows rows);
}
