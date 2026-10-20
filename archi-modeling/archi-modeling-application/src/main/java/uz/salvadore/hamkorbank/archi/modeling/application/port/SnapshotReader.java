package uz.salvadore.hamkorbank.archi.modeling.application.port;

import uz.salvadore.hamkorbank.archi.modeling.application.dto.ModelRows;

/**
 * Обратный путь снимка — откат к версии и замена содержимого извне. Реализует interchange:
 * формат файла — его язык; агрегаты из строк собирает modeling.
 */
public interface SnapshotReader {

    /**
     * Строки модели из снимка; внутренние ключи — новые. Снимок не раскладывается —
     * {@link SnapshotUnreadableException}.
     */
    ModelRows read(byte[] xml);
}
