package uz.salvadore.hamkorbank.archi.modeling.application.port;

/**
 * Снимок версии — файл {@code .archimate}, собранный из базы (UC-MDL-004, п. 3).
 * Реализует interchange: формат файла — его язык (ADR-0017); modeling лишь хранит
 * результат, сжатый, и его отпечаток.
 */
public interface SnapshotWriter {

    /** Несжатый XML. Детерминирован: одно содержимое — одни байты (INV-IXC-004). */
    byte[] write(ModelContent content);
}
