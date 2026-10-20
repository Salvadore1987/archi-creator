package uz.salvadore.hamkorbank.archi.interchange.application.port.in;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ImportArchimateFileCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Импорт файла {@code .archimate}. */
public interface ImportApplicationService {

    /**
     * Импортирует файл новой моделью пространства. Отказ из-за содержимого файла — не исключение,
     * а исход в результате с отчётом; повтор ключа идемпотентности отдаёт итог первой попытки.
     */
    ImportArchimateFileResponse importArchimateFile(EditorIdentity actor, ImportArchimateFileCommand command);
}
