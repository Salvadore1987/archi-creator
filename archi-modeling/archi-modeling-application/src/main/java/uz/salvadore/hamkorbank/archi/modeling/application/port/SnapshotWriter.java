package uz.salvadore.hamkorbank.archi.modeling.application.port;

/**
 * Снимок версии — файл {@code .archimate}, собранный из базы при сохранении.
 * Реализует interchange: формат файла — его язык; modeling лишь хранит
 * результат, сжатый, и его отпечаток.
 */
public interface SnapshotWriter {

    /** Несжатый XML. Детерминирован: одно содержимое — одни байты. */
    byte[] write(ModelContent content);
}
