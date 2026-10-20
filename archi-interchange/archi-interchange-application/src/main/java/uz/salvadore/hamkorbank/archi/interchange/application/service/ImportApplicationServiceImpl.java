package uz.salvadore.hamkorbank.archi.interchange.application.service;

import uz.salvadore.hamkorbank.archi.interchange.application.dto.command.ImportArchimateFileCommand;
import uz.salvadore.hamkorbank.archi.interchange.application.dto.result.ImportArchimateFileResponse;
import uz.salvadore.hamkorbank.archi.interchange.application.handler.ArchimateFileImportCommandHandler;
import uz.salvadore.hamkorbank.archi.interchange.application.port.in.ImportApplicationService;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.EditorIdentity;

/** Импорт файла: делегирует в handler сценария. */
public final class ImportApplicationServiceImpl implements ImportApplicationService {

    private final ArchimateFileImportCommandHandler importFile;

    public ImportApplicationServiceImpl(ArchimateFileImportCommandHandler importFile) {
        this.importFile = importFile;
    }

    @Override
    public ImportArchimateFileResponse importArchimateFile(EditorIdentity actor, ImportArchimateFileCommand command) {
        return importFile.handle(actor, command);
    }
}
