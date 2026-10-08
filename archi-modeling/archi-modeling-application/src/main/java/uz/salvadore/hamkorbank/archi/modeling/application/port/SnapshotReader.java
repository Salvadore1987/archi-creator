package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.time.Instant;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;

/**
 * Обратный путь снимка — откат к версии (UC-MDL-004, {@code RollbackToVersion}).
 * Реализует interchange.
 */
public interface SnapshotReader {

    /**
     * Содержимое снимка как новое содержимое существующей модели: ключ, рабочее
     * пространство, автор и время создания берутся из {@code current}, всё прочее —
     * из снимка; внутренние ключи содержимого — новые.
     */
    ModelContent read(byte[] xml, ModelHeader current, Instant now);
}
