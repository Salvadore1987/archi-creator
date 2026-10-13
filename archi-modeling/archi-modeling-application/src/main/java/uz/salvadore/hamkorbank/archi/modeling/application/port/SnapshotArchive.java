package uz.salvadore.hamkorbank.archi.modeling.application.port;

import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.ModelHeader;
import uz.salvadore.hamkorbank.archi.modeling.domain.version.ModelVersion;

/**
 * Откуда вернуть очищенный снимок версии — из коммита, с которым версия связана.
 * Реализует interchange: формат репозитория — его язык. Восстановленный снимок
 * обязан совпасть с отпечатком версии, иначе его нет.
 */
public interface SnapshotArchive {

    /** Несжатый {@code .archimate}; вернуть неоткуда — пусто. */
    Optional<byte[]> restore(ModelHeader model, ModelVersion version);

    SnapshotArchive NONE = (model, version) -> Optional.empty();
}
